package tech.artcoded.websitev2.pages.invoice;

import java.io.IOException;
import org.springframework.stereotype.Component;
import tech.artcoded.websitev2.rest.util.PdfToolBox;

/** Isolates the external PDF executable from invoice orchestration. */
@Component
public class InvoicePdfRenderer {
    public byte[] render(String html) throws IOException, InterruptedException {
        return PdfToolBox.generatePDFFromHTML(html);
    }
}
