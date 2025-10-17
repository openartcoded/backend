package tech.artcoded.websitev2.rest.util;

import com.github.jhonnymertz.wkhtmltopdf.wrapper.Pdf;
import com.github.jhonnymertz.wkhtmltopdf.wrapper.configurations.WrapperConfig;

import java.io.IOException;

public interface PdfToolBox {

    static byte[] generatePDFFromHTML(String html) throws IOException, InterruptedException {
        Pdf pdf = new Pdf(new WrapperConfig(WrapperConfig.findExecutable()));
        pdf.addPageFromString(html);
        return pdf.getPDF();
    }
}
