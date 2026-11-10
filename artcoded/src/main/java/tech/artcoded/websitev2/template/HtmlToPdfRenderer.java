package tech.artcoded.websitev2.template;

import java.io.IOException;

import org.springframework.stereotype.Component;

import tech.artcoded.websitev2.rest.util.PdfToolBox;

/** Prints the html of a v1 template to a pdf with the backend chromium. A bean so tests can replace it. */
@Component
public class HtmlToPdfRenderer {
    public byte[] render(String html) throws IOException {
        return PdfToolBox.generatePDFFromHTML(html);
    }
}
