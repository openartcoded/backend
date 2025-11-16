package tech.artcoded.websitev2.rest.util;

import com.github.jhonnymertz.wkhtmltopdf.wrapper.Pdf;
import com.github.jhonnymertz.wkhtmltopdf.wrapper.configurations.WrapperConfig;
import com.github.jhonnymertz.wkhtmltopdf.wrapper.params.Param;

import java.io.IOException;

public interface PdfToolBox {

    static byte[] generatePDFFromHTML(String html) throws IOException, InterruptedException {
        var wc = new WrapperConfig(WrapperConfig.findExecutable());
        Pdf pdf = new Pdf(wc);
        pdf.addPageFromString(html);
        pdf.addParam(new Param("--encoding", "UTF-8"));
        return pdf.getPDF();
    }
}
