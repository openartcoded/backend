package tech.artcoded.websitev2.rest.util;

import com.github.jhonnymertz.wkhtmltopdf.wrapper.Pdf;
import com.github.jhonnymertz.wkhtmltopdf.wrapper.configurations.WrapperConfig;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.jsoup.select.Elements;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public interface PdfToolBox {

  static byte[] generatePDFFromHTML(byte[] html) {
    return generatePDFFromHTML(html, true);
  }

  static byte[] generatePDFFromHTML(byte[] html, boolean responsiveImage) {
    try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
      PdfRendererBuilder builder = new PdfRendererBuilder();
      builder.useFastMode();
      builder.withW3cDocument(html5ParseDocument(new String(html), responsiveImage), null);
      builder.toStream(os);
      builder.run();
      return os.toByteArray();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  static org.w3c.dom.Document html5ParseDocument(String html, boolean responsiveImage) {

    org.jsoup.nodes.Document doc = Jsoup.parse(html);
    if (responsiveImage) {
      Elements imgs = doc.select("img");
      imgs.attr("style", "width: 100%;height:auto;");
    }

    return new W3CDom().fromJsoup(doc);
  }

  static byte[] generatePDFFromHTMLV2(String html) throws IOException, InterruptedException {
    Pdf pdf = new Pdf(new WrapperConfig(WrapperConfig.findExecutable()));
    pdf.addPageFromString(html);
    return pdf.getPDF();
  }
}
