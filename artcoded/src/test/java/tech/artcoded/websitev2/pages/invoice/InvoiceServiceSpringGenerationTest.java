package tech.artcoded.websitev2.pages.invoice;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.multipart.MultipartFile;
import tech.artcoded.websitev2.domain.common.RateType;
import tech.artcoded.websitev2.notification.NotificationService;
import tech.artcoded.websitev2.pages.client.BillableClientService;
import tech.artcoded.websitev2.pages.personal.PersonalInfo;
import tech.artcoded.websitev2.pages.personal.PersonalInfoService;
import tech.artcoded.websitev2.pages.timesheet.TimesheetRepository;
import tech.artcoded.websitev2.upload.FileUpload;
import tech.artcoded.websitev2.upload.IFileUploadService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Uses a real Spring-created invoice service, but never invokes the PDF executable or MongoDB. */
class InvoiceServiceSpringGenerationTest {
    private final PersonalInfoService personal = mock(PersonalInfoService.class);
    private final InvoiceTemplateRepository templates = mock(InvoiceTemplateRepository.class);
    private final BillableClientService clients = mock(BillableClientService.class);
    private final TimesheetRepository timesheets = mock(TimesheetRepository.class);
    private final IFileUploadService uploads = mock(IFileUploadService.class);
    private final InvoiceGenerationRepository invoices = mock(InvoiceGenerationRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final ProducerTemplate events = mock(ProducerTemplate.class);
    private final InvoicePdfRenderer renderer = mock(InvoicePdfRenderer.class);

    private ApplicationContextRunner spring() {
        return new ApplicationContextRunner()
                .withBean(PersonalInfoService.class, () -> personal)
                .withBean(InvoiceTemplateRepository.class, () -> templates)
                .withBean(BillableClientService.class, () -> clients)
                .withBean(TimesheetRepository.class, () -> timesheets)
                .withBean(IFileUploadService.class, () -> uploads)
                .withBean(InvoiceGenerationRepository.class, () -> invoices)
                .withBean(NotificationService.class, () -> notifications)
                .withBean(ProducerTemplate.class, () -> events)
                .withBean(InvoicePdfRenderer.class, () -> renderer)
                .withBean(InvoiceService.class);
    }

    private InvoiceGeneration creditNote() {
        return InvoiceGeneration.builder()
                .invoiceNumber("REGRESSION-123")
                .seqInvoiceNumber(null)
                .billTo(BillTo.builder().vatNumber("BE0123456789").build())
                .invoiceTable(List.of(InvoiceRow.builder()
                        .amount(new BigDecimal("-1"))
                        .rate(new BigDecimal("100"))
                        .rateType(RateType.HOURS).amountType(RateType.HOURS).build()))
                .freemarkerTemplateId("template-1")
                .skipPeppol(true)
                .build();
    }

    @Test
    void createsAndUploadsInvoiceWithMockedPdfRendering() throws Exception {
        when(invoices.save(any(InvoiceGeneration.class))).thenAnswer(a -> a.getArgument(0));
        when(templates.findById("template-1"))
                .thenReturn(Optional.of(InvoiceFreemarkerTemplate.builder().templateUploadId("html-1").build()));
        FileUpload logo = FileUpload.builder().originalFilename("logo.png").build();
        FileUpload html = FileUpload.builder().originalFilename("invoice.ftl").build();
        when(personal.get()).thenReturn(PersonalInfo.builder().logoUploadId("logo-1").build());
        when(uploads.findOneById("logo-1")).thenReturn(Optional.of(logo));
        when(uploads.findOneById("html-1")).thenReturn(Optional.of(html));
        when(uploads.uploadToByteArray(logo)).thenReturn(new byte[] {1, 2, 3});
        when(uploads.uploadToInputStream(html)).thenAnswer(a ->
                new java.io.ByteArrayInputStream("Invoice ${invoice.reference}; ${logo}".getBytes(StandardCharsets.UTF_8)));
        when(renderer.render(anyString())).thenReturn("fake-pdf".getBytes(StandardCharsets.UTF_8));
        when(uploads.upload(any(MultipartFile.class), anyString(), eq(false))).thenReturn("pdf-upload-1");

        spring().run(context -> {
            assertThat(context).hasSingleBean(InvoiceService.class);
            InvoiceGeneration partial = context.getBean(InvoiceService.class).generateInvoice(creditNote());
            assertThat(partial.isLocked()).isTrue();
            assertThat(partial.getStructuredReference()).isNotBlank();

            // Mockito timeout awaits the worker virtual thread without a fixed sleep.
            verify(renderer, timeout(5000)).render(contains("Invoice REGRESSION-123"));
            verify(uploads, timeout(5000)).upload(argThat(file -> {
                try {
                    return file.getContentType().equals("application/pdf")
                            && java.util.Arrays.equals(file.getBytes(), "fake-pdf".getBytes(StandardCharsets.UTF_8));
                } catch (java.io.IOException e) {
                    return false;
                }
            }), eq(partial.getId()), eq(false));
            verify(invoices, timeout(5000).times(2)).save(any(InvoiceGeneration.class));
            verify(notifications, timeout(5000)).sendEvent(startsWith("New Invoice Ready"), eq("NEW_INVOICE"), eq(partial.getId()));
            verify(events, timeout(5000)).sendBody(anyString(), eq(org.apache.camel.ExchangePattern.InOnly), any());
        });
    }

    @Test
    void missingReferenceIsRejectedBeforeStorageOrRendering() {
        spring().run(context -> {
            var input = creditNote();
            input.setInvoiceNumber("");
            assertThatThrownBy(() -> context.getBean(InvoiceService.class).generateInvoice(input))
                    .isInstanceOf(RuntimeException.class).hasMessageContaining("reference number is empty");
            verifyNoInteractions(renderer, uploads);
            verify(invoices, never()).save(any());
        });
    }

    @Test
    void existingSequentialNumberIsRejectedBeforeStorageOrRendering() {
        spring().run(context -> {
            var input = creditNote();
            input.setSeqInvoiceNumber(123L);
            assertThatThrownBy(() -> context.getBean(InvoiceService.class).generateInvoice(input))
                    .isInstanceOf(RuntimeException.class).hasMessageContaining("seq invoice number should be null");
            verifyNoInteractions(renderer, uploads);
            verify(invoices, never()).save(any());
        });
    }

    @Test
    void pendingOrDeletedInvoicePreventsNewGeneration() {
        when(invoices.countByLogicalDeleteIsOrArchivedIs(true, false)).thenReturn(1L);
        spring().run(context -> {
            assertThatThrownBy(() -> context.getBean(InvoiceService.class).generateInvoice(creditNote()))
                    .hasMessageContaining("at least one being processed");
            verify(invoices, never()).save(any());
            verifyNoInteractions(renderer);
        });
    }

    @Test
    void duplicateReferencePreventsNewGeneration() {
        when(invoices.existsByInvoiceNumber("REGRESSION-123")).thenReturn(true);
        spring().run(context -> {
            assertThatThrownBy(() -> context.getBean(InvoiceService.class).generateInvoice(creditNote()))
                    .hasMessageContaining("already exist");
            verify(invoices, never()).save(any());
            verifyNoInteractions(renderer);
        });
    }
}
