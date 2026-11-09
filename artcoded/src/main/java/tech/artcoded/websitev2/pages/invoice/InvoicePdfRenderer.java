package tech.artcoded.websitev2.pages.invoice;

import java.io.IOException;
import org.springframework.stereotype.Component;
import tech.artcoded.websitev2.rest.util.PdfToolBox;

/** Isolates PDF rendering (OpenHTMLtoPDF) from invoice orchestration, so tests can replace it. */
@Component
public class InvoicePdfRenderer {
    public byte[] render(String html) throws IOException {
        return PdfToolBox.generatePDFFromHTML(html);
    }
}
