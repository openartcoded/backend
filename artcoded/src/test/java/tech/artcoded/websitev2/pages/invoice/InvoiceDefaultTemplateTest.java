package tech.artcoded.websitev2.pages.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.apache.camel.ProducerTemplate;
import org.openapitools.client.api.TemplateRoutesApi;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import tech.artcoded.websitev2.domain.common.RateType;
import tech.artcoded.websitev2.notification.NotificationService;
import tech.artcoded.websitev2.pages.client.BillableClientService;
import tech.artcoded.websitev2.pages.personal.PersonalInfo;
import tech.artcoded.websitev2.pages.personal.PersonalInfoService;
import tech.artcoded.websitev2.pages.timesheet.TimesheetRepository;
import tech.artcoded.websitev2.template.HtmlToPdfRenderer;
import tech.artcoded.websitev2.template.OaTemplate;
import tech.artcoded.websitev2.template.OaTemplateRenderer;
import tech.artcoded.websitev2.template.OaTemplateRepository;
import tech.artcoded.websitev2.template.OaTemplateService;
import tech.artcoded.websitev2.template.OaTemplateType;
import tech.artcoded.websitev2.template.OaTemplateVersion;
import tech.artcoded.websitev2.upload.IFileUploadService;

/** The default invoice template is used for new invoices, with a fallback to the latest invoice template. */
class InvoiceDefaultTemplateTest {
    private final PersonalInfoService personal = mock(PersonalInfoService.class);
    private final OaTemplateRepository templates = mock(OaTemplateRepository.class);
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final InvoiceGenerationRepository invoices = mock(InvoiceGenerationRepository.class);
    private final IFileUploadService uploads = mock(IFileUploadService.class);

    private static final OaTemplateType INVOICE = OaTemplateType.INVOICE;
    private static final OaTemplate LATEST = OaTemplate.builder().id("latest").name("latest").type(INVOICE).build();
    private static final OaTemplate DEFAULT = OaTemplate.builder().id("default").name("default").type(INVOICE)
            .defaultTemplate(true).build();

    private ApplicationContextRunner spring() {
        return new ApplicationContextRunner().withBean(PersonalInfoService.class, () -> personal)
                .withBean(OaTemplateRepository.class, () -> templates)
                .withBean(TemplateRoutesApi.class, () -> mock(TemplateRoutesApi.class))
                .withBean(MongoTemplate.class, () -> mongo).withBean(OaTemplateService.class)
                .withBean(HtmlToPdfRenderer.class, () -> mock(HtmlToPdfRenderer.class))
                .withBean(OaTemplateRenderer.class)
                .withBean(BillableClientService.class, () -> mock(BillableClientService.class))
                .withBean(TimesheetRepository.class, () -> mock(TimesheetRepository.class))
                .withBean(IFileUploadService.class, () -> uploads)
                .withBean(InvoiceGenerationRepository.class, () -> invoices)
                .withBean(NotificationService.class, () -> mock(NotificationService.class))
                .withBean(ProducerTemplate.class, () -> mock(ProducerTemplate.class))
                .withBean(InvoiceService.class);
    }

    @BeforeEach
    void setUp() {
        when(personal.get()).thenReturn(PersonalInfo.builder().maxDaysToPay(30).build());
        when(templates.findFirstByTypeAndDefaultTemplateIsTrueAndLogicalDeleteIsFalse(INVOICE)).thenReturn(Optional.empty());
        when(templates.save(any(OaTemplate.class))).thenAnswer(a -> a.getArgument(0));
    }

    private InvoiceGeneration existing(String templateId) {
        return InvoiceGeneration.builder().id("existing").freemarkerTemplateId(templateId)
                .invoiceTable(List.of(InvoiceRow.builder().rate(BigDecimal.TEN).amount(BigDecimal.ONE)
                        .rateType(RateType.HOURS).amountType(RateType.HOURS).build()))
                .build();
    }

    @Test
    void timesheetInvoiceUsesTheDefaultTemplate() {
        when(templates.findFirstByTypeAndDefaultTemplateIsTrueAndLogicalDeleteIsFalse(INVOICE)).thenReturn(Optional.of(DEFAULT));
        spring().run(
                ctx -> assertThat(ctx.getBean(InvoiceService.class).newInvoiceFromNothing().getFreemarkerTemplateId())
                        .isEqualTo("default"));
    }

    @Test
    void newInvoiceUsesTheDefaultTemplate() {
        when(templates.findFirstByTypeAndDefaultTemplateIsTrueAndLogicalDeleteIsFalse(INVOICE)).thenReturn(Optional.of(DEFAULT));
        when(invoices.findFirstByLogicalDeleteIsFalseOrderByDateCreationDesc())
                .thenReturn(Optional.of(existing("other")));
        spring().run(ctx -> assertThat(
                ctx.getBean(InvoiceService.class).newInvoiceFromEmptyTemplate().getFreemarkerTemplateId())
                        .isEqualTo("default"));
    }

    @Test
    void invoiceFromExistingUsesTheDefaultTemplateOverItsOwn() {
        when(templates.findFirstByTypeAndDefaultTemplateIsTrueAndLogicalDeleteIsFalse(INVOICE)).thenReturn(Optional.of(DEFAULT));
        when(invoices.findById("existing")).thenReturn(Optional.of(existing("other")));
        spring().run(ctx -> assertThat(
                ctx.getBean(InvoiceService.class).newInvoiceFromExisting("existing").getFreemarkerTemplateId())
                        .isEqualTo("default"));
    }

    @Test
    void withoutDefaultTheExistingInvoiceKeepsItsOwn() {
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
        when(templates.findByTypeAndDefaultTemplateIsTrue(INVOICE)).thenReturn(List.of(previous));
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
        when(templates.findByTypeAndDefaultTemplateIsTrue(INVOICE)).thenReturn(List.of(DEFAULT));
        spring().run(ctx -> {
            ctx.getBean(InvoiceService.class).setDefaultTemplate("default", true);
            verify(templates, never()).save(argThat(t -> !t.isDefaultTemplate()));
        });
    }

    @Test
    void theDefaultCannotBeUnsetOnlyReplaced() {
        when(templates.findById("default")).thenReturn(Optional.of(DEFAULT));
        spring().run(ctx -> {
            assertThatThrownBy(() -> ctx.getBean(InvoiceService.class).setDefaultTemplate("default", false))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(templates, never()).save(any());
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
    void theDefaultTemplateCannotBeDeleted() {
        when(templates.findById("default")).thenReturn(Optional.of(DEFAULT));
        spring().run(ctx -> {
            assertThatThrownBy(() -> ctx.getBean(InvoiceService.class).deleteTemplate("default"))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("default");
            verify(templates, never()).save(any());
            verify(templates, never()).deleteById(any());
        });
    }

    @Test
    void aTemplateStillUsedIsOnlyFlaggedAsDeleted() {
        when(templates.findById("latest")).thenReturn(Optional.of(LATEST));
        when(mongo.exists(any(Query.class), eq(InvoiceGeneration.class))).thenReturn(true);
        spring().run(ctx -> {
            ctx.getBean(InvoiceService.class).deleteTemplate("latest");
            verify(templates).save(argThat(t -> t.getId().equals("latest") && t.isLogicalDelete()));
            verify(templates, never()).deleteById(any());
        });
    }

    @Test
    void aTemplateOfAnotherTypeCannotBeTheDefaultInvoiceTemplate() {
        when(templates.findById("cv")).thenReturn(Optional.of(LATEST.toBuilder().id("cv").type(OaTemplateType.CV)
                .build()));
        spring().run(ctx -> {
            assertThat(ctx.getBean(InvoiceService.class).setDefaultTemplate("cv", true)).isEmpty();
            verify(templates, never()).save(any());
        });
    }

    @Test
    void anUnusedTemplateIsReallyDeleted() {
        when(templates.findById("latest")).thenReturn(Optional.of(LATEST.toBuilder().version(OaTemplateVersion.V1)
                .templateUploadId("upl").build()));
        spring().run(ctx -> {
            ctx.getBean(InvoiceService.class).deleteTemplate("latest");
            verify(templates).deleteById("latest");
            verify(uploads).deleteByCorrelationId("latest");
            verify(templates, never()).save(any());
        });
    }
}
