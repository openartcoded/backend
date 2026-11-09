package tech.artcoded.websitev2.pages.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import tech.artcoded.websitev2.domain.common.RateType;
import tech.artcoded.websitev2.notification.NotificationService;
import tech.artcoded.websitev2.pages.client.BillableClientService;
import tech.artcoded.websitev2.pages.personal.PersonalInfo;
import tech.artcoded.websitev2.pages.personal.PersonalInfoService;
import tech.artcoded.websitev2.pages.timesheet.TimesheetRepository;
import tech.artcoded.websitev2.upload.IFileUploadService;

/** The default invoice template is used for new invoices, with a fallback to the latest template. */
class InvoiceDefaultTemplateTest {
    private final PersonalInfoService personal = mock(PersonalInfoService.class);
    private final InvoiceTemplateRepository templates = mock(InvoiceTemplateRepository.class);
    private final InvoiceGenerationRepository invoices = mock(InvoiceGenerationRepository.class);
    private final IFileUploadService uploads = mock(IFileUploadService.class);

    private static final InvoiceFreemarkerTemplate LATEST = InvoiceFreemarkerTemplate.builder().id("latest")
            .name("latest").build();
    private static final InvoiceFreemarkerTemplate DEFAULT = InvoiceFreemarkerTemplate.builder().id("default")
            .name("default").defaultTemplate(true).build();

    private ApplicationContextRunner spring() {
        return new ApplicationContextRunner().withBean(PersonalInfoService.class, () -> personal)
                .withBean(InvoiceTemplateRepository.class, () -> templates)
                .withBean(BillableClientService.class, () -> mock(BillableClientService.class))
                .withBean(TimesheetRepository.class, () -> mock(TimesheetRepository.class))
                .withBean(IFileUploadService.class, () -> uploads)
                .withBean(InvoiceGenerationRepository.class, () -> invoices)
                .withBean(NotificationService.class, () -> mock(NotificationService.class))
                .withBean(ProducerTemplate.class, () -> mock(ProducerTemplate.class))
                .withBean(InvoicePdfRenderer.class, () -> mock(InvoicePdfRenderer.class))
                .withBean(InvoiceService.class);
    }

    @BeforeEach
    void setUp() {
        when(personal.get()).thenReturn(PersonalInfo.builder().maxDaysToPay(30).build());
        when(templates.findTop1ByOrderByDateCreationDesc()).thenReturn(Optional.of(LATEST));
        when(templates.findFirstByDefaultTemplateIsTrueAndLogicalDeleteIsFalse()).thenReturn(Optional.empty());
        when(templates.save(any(InvoiceFreemarkerTemplate.class))).thenAnswer(a -> a.getArgument(0));
    }

    private InvoiceGeneration existing(String templateId) {
        return InvoiceGeneration.builder().id("existing").freemarkerTemplateId(templateId)
                .invoiceTable(List.of(InvoiceRow.builder().rate(BigDecimal.TEN).amount(BigDecimal.ONE)
                        .rateType(RateType.HOURS).amountType(RateType.HOURS).build()))
                .build();
    }

    @Test
    void timesheetInvoiceUsesTheDefaultTemplate() {
        when(templates.findFirstByDefaultTemplateIsTrueAndLogicalDeleteIsFalse()).thenReturn(Optional.of(DEFAULT));
        spring().run(
                ctx -> assertThat(ctx.getBean(InvoiceService.class).newInvoiceFromNothing().getFreemarkerTemplateId())
                        .isEqualTo("default"));
    }

    @Test
    void newInvoiceUsesTheDefaultTemplate() {
        when(templates.findFirstByDefaultTemplateIsTrueAndLogicalDeleteIsFalse()).thenReturn(Optional.of(DEFAULT));
        when(invoices.findFirstByLogicalDeleteIsFalseOrderByDateCreationDesc())
                .thenReturn(Optional.of(existing("other")));
        spring().run(ctx -> assertThat(
                ctx.getBean(InvoiceService.class).newInvoiceFromEmptyTemplate().getFreemarkerTemplateId())
                        .isEqualTo("default"));
    }

    @Test
    void invoiceFromExistingUsesTheDefaultTemplateOverItsOwn() {
        when(templates.findFirstByDefaultTemplateIsTrueAndLogicalDeleteIsFalse()).thenReturn(Optional.of(DEFAULT));
        when(invoices.findById("existing")).thenReturn(Optional.of(existing("other")));
        spring().run(ctx -> assertThat(
                ctx.getBean(InvoiceService.class).newInvoiceFromExisting("existing").getFreemarkerTemplateId())
                        .isEqualTo("default"));
    }

    @Test
    void withoutDefaultTheLatestTemplateIsUsedAsBefore() {
        when(invoices.findById("existing")).thenReturn(Optional.of(existing("other")));
        spring().run(ctx -> {
            var service = ctx.getBean(InvoiceService.class);
            assertThat(service.newInvoiceFromNothing().getFreemarkerTemplateId()).isEqualTo("latest");
            assertThat(service.newInvoiceFromExisting("existing").getFreemarkerTemplateId()).isEqualTo("latest");
        });
    }

    @Test
    void withoutAnyTemplateTheExistingInvoiceKeepsItsOwn() {
        when(templates.findTop1ByOrderByDateCreationDesc()).thenReturn(Optional.empty());
        when(invoices.findById("existing")).thenReturn(Optional.of(existing("other")));
        spring().run(ctx -> {
            var service = ctx.getBean(InvoiceService.class);
            assertThat(service.newInvoiceFromExisting("existing").getFreemarkerTemplateId()).isEqualTo("other");
            assertThat(service.newInvoiceFromNothing().getFreemarkerTemplateId()).isNull();
        });
    }

    @Test
    void settingADefaultUnsetsThePreviousOne() {
        var previous = DEFAULT.toBuilder().id("previous").build();
        when(templates.findById("latest")).thenReturn(Optional.of(LATEST));
        when(templates.findByDefaultTemplateIsTrue()).thenReturn(List.of(previous));
        spring().run(ctx -> {
            var saved = ctx.getBean(InvoiceService.class).setDefaultTemplate("latest", true);
            assertThat(saved).hasValueSatisfying(t -> assertThat(t.isDefaultTemplate()).isTrue());
            verify(templates).save(argThat(t -> t.getId().equals("previous") && !t.isDefaultTemplate()));
            verify(templates).save(argThat(t -> t.getId().equals("latest") && t.isDefaultTemplate()));
        });
    }

    @Test
    void settingTheCurrentDefaultAgainKeepsIt() {
        when(templates.findById("default")).thenReturn(Optional.of(DEFAULT));
        when(templates.findByDefaultTemplateIsTrue()).thenReturn(List.of(DEFAULT));
        spring().run(ctx -> {
            ctx.getBean(InvoiceService.class).setDefaultTemplate("default", true);
            verify(templates, never()).save(argThat(t -> !t.isDefaultTemplate()));
        });
    }

    @Test
    void aDefaultCanBeUnset() {
        when(templates.findById("default")).thenReturn(Optional.of(DEFAULT));
        spring().run(ctx -> {
            var saved = ctx.getBean(InvoiceService.class).setDefaultTemplate("default", false);
            assertThat(saved).hasValueSatisfying(t -> assertThat(t.isDefaultTemplate()).isFalse());
            verify(templates, never()).findByDefaultTemplateIsTrue();
        });
    }

    @Test
    void aMissingOrDeletedTemplateCannotBeTheDefault() {
        when(templates.findById("missing")).thenReturn(Optional.empty());
        when(templates.findById("deleted"))
                .thenReturn(Optional.of(LATEST.toBuilder().id("deleted").logicalDelete(true).build()));
        spring().run(ctx -> {
            var service = ctx.getBean(InvoiceService.class);
            assertThat(service.setDefaultTemplate("missing", true)).isEmpty();
            assertThat(service.setDefaultTemplate("deleted", true)).isEmpty();
            verify(templates, never()).save(any());
        });
    }

    @Test
    void deletingTheDefaultTemplateUnsetsIt() {
        when(templates.findById("default")).thenReturn(Optional.of(DEFAULT));
        when(invoices.countByFreemarkerTemplateId("default")).thenReturn(3L);
        spring().run(ctx -> {
            ctx.getBean(InvoiceService.class).deleteTemplate("default");
            verify(templates).save(argThat(t -> t.isLogicalDelete() && !t.isDefaultTemplate()));
        });
    }
}
