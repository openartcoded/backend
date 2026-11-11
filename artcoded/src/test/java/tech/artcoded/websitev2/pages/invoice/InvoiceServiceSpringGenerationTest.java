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

import org.openapitools.client.api.TemplateRoutesApi;
import org.springframework.data.mongodb.core.MongoTemplate;
import tech.artcoded.websitev2.template.HtmlToPdfRenderer;
import tech.artcoded.websitev2.template.OaTemplate;
import tech.artcoded.websitev2.template.OaTemplateRenderer;
import tech.artcoded.websitev2.template.OaTemplateRepository;
import tech.artcoded.websitev2.template.OaTemplateService;
import tech.artcoded.websitev2.template.OaTemplateType;
import tech.artcoded.websitev2.template.OaTemplateVersion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Uses a real Spring-created invoice service, but never invokes the PDF executable or MongoDB. */
class InvoiceServiceSpringGenerationTest {
    private final PersonalInfoService personal = mock(PersonalInfoService.class);
    private final OaTemplateRepository templates = mock(OaTemplateRepository.class);
    private final BillableClientService clients = mock(BillableClientService.class);
    private final TimesheetRepository timesheets = mock(TimesheetRepository.class);
    private final IFileUploadService uploads = mock(IFileUploadService.class);
    private final InvoiceGenerationRepository invoices = mock(InvoiceGenerationRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final ProducerTemplate events = mock(ProducerTemplate.class);
    private final HtmlToPdfRenderer renderer = mock(HtmlToPdfRenderer.class);

    private ApplicationContextRunner spring() {
        return new ApplicationContextRunner().withBean(PersonalInfoService.class, () -> personal)
                .withBean(OaTemplateRepository.class, () -> templates)
                .withBean(TemplateRoutesApi.class, () -> mock(TemplateRoutesApi.class))
                .withBean(MongoTemplate.class, () -> mock(MongoTemplate.class)).withBean(OaTemplateService.class)
                .withBean(HtmlToPdfRenderer.class, () -> renderer).withBean(OaTemplateRenderer.class)
                .withBean(BillableClientService.class, () -> clients)
                .withBean(TimesheetRepository.class, () -> timesheets).withBean(IFileUploadService.class, () -> uploads)
                .withBean(InvoiceGenerationRepository.class, () -> invoices)
                .withBean(NotificationService.class, () -> notifications).withBean(ProducerTemplate.class, () -> events)
                .withBean(InvoiceService.class);
    }

    private InvoiceGeneration creditNote() {
        return InvoiceGeneration.builder().invoiceNumber("REGRESSION-123").seqInvoiceNumber(null)
                .billTo(BillTo.builder().vatNumber("BE0123456789").build())
                .invoiceTable(List.of(InvoiceRow.builder().amount(new BigDecimal("-1")).rate(new BigDecimal("100"))
                        .rateType(RateType.HOURS).amountType(RateType.HOURS).build()))
                .freemarkerTemplateId("template-1").skipPeppol(true).build();
    }

    @Test
    void createsAndUploadsInvoiceWithMockedPdfRendering() throws Exception {
        when(invoices.save(any(InvoiceGeneration.class))).thenAnswer(a -> a.getArgument(0));
        when(templates.findById("template-1")).thenReturn(Optional.of(OaTemplate.builder().id("template-1")
                .type(OaTemplateType.INVOICE).version(OaTemplateVersion.V1).templateUploadId("html-1").build()));
        FileUpload logo = FileUpload.builder().originalFilename("logo.png").build();
        FileUpload html = FileUpload.builder().originalFilename("invoice.ftl").build();
        when(personal.get()).thenReturn(PersonalInfo.builder().logoUploadId("logo-1").build());
        when(uploads.findOneById("logo-1")).thenReturn(Optional.of(logo));
        when(uploads.findOneById("html-1")).thenReturn(Optional.of(html));
        when(uploads.uploadToByteArray(logo)).thenReturn(new byte[] { 1, 2, 3 });
        when(uploads.uploadToInputStream(html)).thenAnswer(a -> new java.io.ByteArrayInputStream(
                "Invoice ${invoice.reference}; ${logo}".getBytes(StandardCharsets.UTF_8)));
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
            verify(notifications, timeout(5000)).sendEvent(startsWith("New Invoice Ready"), eq("NEW_INVOICE"),
                    eq(partial.getId()));
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

    // ---------- extra files (attachments) ----------

    private InvoiceGeneration invoiceWithAttachments(boolean skipPeppol, String... attachmentIds) {
        return creditNote().toBuilder().skipPeppol(skipPeppol)
                .invoiceTable(List.of(InvoiceRow.builder().amount(new BigDecimal("1")).rate(new BigDecimal("100"))
                        .rateType(RateType.HOURS).amountType(RateType.HOURS).build()))
                .attachmentIds(new java.util.ArrayList<>(List.of(attachmentIds))).build();
    }

    private static FileUpload upload(String id, String name, String contentType) {
        return FileUpload.builder().id(id).originalFilename(name).contentType(contentType).build();
    }

    @Test
    void attachmentsOfATypePeppolRefusesAreRejectedBeforeStorage() {
        when(uploads.findAll(List.of("a1", "a2"))).thenReturn(
                List.of(upload("a1", "timesheet.pdf", "application/pdf"), upload("a2", "sources.zip", "application/zip")));
        spring().run(context -> {
            assertThatThrownBy(() -> context.getBean(InvoiceService.class)
                    .generateInvoice(invoiceWithAttachments(false, "a1", "a2")))
                    .hasMessageContaining("Peppol only accepts").hasMessageContaining("sources.zip");
            verify(invoices, never()).save(any());
            verifyNoInteractions(renderer);
        });
    }

    @Test
    void anyTypeIsAcceptedWhenTheInvoiceDoesNotGoToPeppol() {
        when(uploads.findAll(List.of("a2"))).thenReturn(List.of(upload("a2", "sources.zip", "application/zip")));
        spring().run(context -> context.getBean(InvoiceService.class)
                .validateAttachments(invoiceWithAttachments(true, "a2")));
    }

    @Test
    void missingAttachmentsAreRejected() {
        when(uploads.findAll(List.of("a1", "gone"))).thenReturn(List.of(upload("a1", "timesheet.pdf", "application/pdf")));
        spring().run(context -> {
            assertThatThrownBy(() -> context.getBean(InvoiceService.class)
                    .generateInvoice(invoiceWithAttachments(false, "a1", "gone")))
                    .hasMessageContaining("not found").hasMessageContaining("gone");
            verify(invoices, never()).save(any());
        });
    }

    @Test
    void duplicatedOrBlankAttachmentIdsAreIgnored() {
        var invoice = invoiceWithAttachments(false, "a1", "a1", " ");
        org.assertj.core.api.Assertions.assertThat(InvoiceService.attachmentIds(invoice)).containsExactly("a1");
        invoice.setAttachmentIds(null);
        org.assertj.core.api.Assertions.assertThat(InvoiceService.attachmentIds(invoice)).isEmpty();
    }

    @Test
    void reallyDeletingAnInvoiceDeletesItsFiles() {
        var invoice = invoiceWithAttachments(false, "a1", "a2").toBuilder().id("inv-1").invoiceUploadId("pdf-1")
                .invoiceUBLId("ubl-1").build();
        when(invoices.findById("inv-1")).thenReturn(Optional.of(invoice));
        spring().run(context -> {
            context.getBean(InvoiceService.class).delete("inv-1", false);
            verify(invoices).delete(invoice);
            verify(uploads).delete("pdf-1");
            verify(uploads).delete("ubl-1");
            verify(uploads).delete("a1");
            verify(uploads).delete("a2");
        });
    }

    @Test
    void aLogicalDeleteKeepsTheFiles() {
        var invoice = invoiceWithAttachments(false, "a1").toBuilder().id("inv-1").invoiceUploadId("pdf-1").build();
        when(invoices.findById("inv-1")).thenReturn(Optional.of(invoice));
        spring().run(context -> {
            context.getBean(InvoiceService.class).delete("inv-1", true);
            verify(uploads, never()).delete(anyString());
            verify(invoices).save(argThat(InvoiceGeneration::isLogicalDelete));
        });
    }

    @Test
    void aNewInvoiceFromAnExistingOneDoesNotTakeItsFiles() {
        when(invoices.findById("inv-1")).thenReturn(Optional.of(invoiceWithAttachments(false, "a1")));
        spring().run(context -> {
            var copy = context.getBean(InvoiceService.class).newInvoiceFromExisting("inv-1");
            org.assertj.core.api.Assertions.assertThat(copy.getAttachmentIds()).isEmpty();
        });
    }

    @Test
    void aCreditNoteDoesNotShareTheFilesOfItsInvoice() {
        // the credit note would otherwise delete the files of the invoice when it is really deleted
        var invoice = invoiceWithAttachments(false, "a1").toBuilder().id("inv-1").archived(true).build();
        when(invoices.findById("inv-1")).thenReturn(Optional.of(invoice));
        when(invoices.save(any(InvoiceGeneration.class))).thenAnswer(a -> a.getArgument(0));
        spring().run(context -> {
            var creditNote = context.getBean(InvoiceService.class).makeCreditNote("inv-1");
            org.assertj.core.api.Assertions.assertThat(creditNote.isCreditNote()).isTrue();
            org.assertj.core.api.Assertions.assertThat(creditNote.getAttachmentIds()).isEmpty();
            // the invoice keeps its files
            verify(invoices).save(argThat(i -> "inv-1".equals(i.getId()) && i.getAttachmentIds().contains("a1")));
            // the generation runs in the background (and fails here, nothing is rendered): wait for it to give
            // back the lock, otherwise another test could see "an invoice is already being created"
            verify(notifications, timeout(5000)).sendEvent(eq("could not create invoice, check logs"), anyString(),
                    anyString());
            long deadline = System.currentTimeMillis() + 5000;
            while (InvoiceService.SEMAPHORE.availablePermits() == 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
        });
    }
}
