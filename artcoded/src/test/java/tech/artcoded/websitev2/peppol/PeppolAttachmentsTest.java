package tech.artcoded.websitev2.peppol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import tech.artcoded.websitev2.peppol.PeppolAttachments.Attachment;

class PeppolAttachmentsTest {

    static byte[] resource(String name) throws Exception {
        return new ClassPathResource(name).getContentAsByteArray();
    }

    static final Attachment TIMESHEET = new Attachment("timesheet.pdf", "application/pdf",
            "%PDF-timesheet".getBytes(StandardCharsets.UTF_8));
    static final Attachment SCREENSHOT = new Attachment("proof.png", "image/png", new byte[] { 1, 2, 3 });

    @Test
    void addsTheAttachmentsAfterTheInvoicePdfAndStaysValidUbl() throws Exception {
        var original = resource("peppol-invoice-example.xml");
        var before = PeppolParserUtil.parseInvoice(original).getAttachments();

        var ubl = PeppolAttachments.addAttachments(original, List.of(TIMESHEET, SCREENSHOT));

        // parsing validates against the UBL schema: the elements are where they must be
        var attachments = PeppolParserUtil.parseInvoice(ubl).getAttachments();
        assertThat(attachments).hasSize(before.size() + 2);
        // the existing ones first, unchanged, then the new ones in order
        assertThat(attachments.subList(0, before.size())).extracting("originalFilename")
                .isEqualTo(before.stream().map(a -> a.getOriginalFilename()).toList());
        var added = attachments.subList(before.size(), attachments.size());
        assertThat(added).extracting("originalFilename").containsExactly("timesheet.pdf", "proof.png");
        assertThat(added).extracting("contentType").containsExactly("application/pdf", "image/png");
        assertThat(added.get(0).getBytes()).isEqualTo(TIMESHEET.content());
        assertThat(added.get(1).getBytes()).isEqualTo(SCREENSHOT.content());
    }

    @Test
    void worksForACreditNote() throws Exception {
        var original = resource("peppol-creditnote-example.xml");
        var before = PeppolParserUtil.parseCreditNote(original).getAttachments().size();

        var ubl = PeppolAttachments.addAttachments(original, List.of(TIMESHEET));

        assertThat(PeppolParserUtil.parseCreditNote(ubl).getAttachments()).hasSize(before + 1);
    }

    @Test
    void addsTheFirstReferenceBeforeTheSupplierWhenThereIsNone() throws Exception {
        var original = new String(resource("peppol-invoice-example.xml"), StandardCharsets.UTF_8)
                .replaceAll("(?s)<cac:AdditionalDocumentReference>.*?</cac:AdditionalDocumentReference>", "")
                .getBytes(StandardCharsets.UTF_8);
        assertThat(PeppolParserUtil.parseInvoice(original).getAttachments()).isEmpty();

        var ubl = PeppolAttachments.addAttachments(original, List.of(TIMESHEET));

        var xml = new String(ubl, StandardCharsets.UTF_8);
        assertThat(xml.indexOf("AdditionalDocumentReference")).isLessThan(xml.indexOf("AccountingSupplierParty"));
        assertThat(PeppolParserUtil.parseInvoice(ubl).getAttachments()).extracting("originalFilename")
                .containsExactly("timesheet.pdf");
    }

    @Test
    void nothingToAddKeepsTheDocumentAsIs() throws Exception {
        var original = resource("peppol-invoice-example.xml");
        assertThat(PeppolAttachments.addAttachments(original, List.of())).isSameAs(original);
        assertThat(PeppolAttachments.addAttachments(original, null)).isSameAs(original);
    }

    @Test
    void refusesTheTypesPeppolDoesNotAccept() throws Exception {
        var original = resource("peppol-invoice-example.xml");
        var zip = new Attachment("archive.zip", "application/zip", new byte[] { 1 });
        assertThatThrownBy(() -> PeppolAttachments.addAttachments(original, List.of(TIMESHEET, zip)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("archive.zip");
    }

    @Test
    void allowedTypes() {
        assertThat(PeppolAttachments.isAllowed("application/pdf")).isTrue();
        assertThat(PeppolAttachments.isAllowed("IMAGE/JPEG")).isTrue();
        assertThat(PeppolAttachments.isAllowed("text/csv")).isTrue();
        assertThat(PeppolAttachments.isAllowed("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .isTrue();
        assertThat(PeppolAttachments.isAllowed("application/zip")).isFalse();
        assertThat(PeppolAttachments.isAllowed("application/msword")).isFalse();
        assertThat(PeppolAttachments.isAllowed(null)).isFalse();
    }
}
