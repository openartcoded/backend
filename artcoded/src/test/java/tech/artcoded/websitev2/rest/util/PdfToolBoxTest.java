package tech.artcoded.websitev2.rest.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Renders real PDFs with headless chromium and reads them back with PDFBox. Skipped when chromium cannot be started
 * (the first run downloads it into ~/.cache/ms-playwright unless PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD is set).
 */
class PdfToolBoxTest {

    @BeforeAll
    static void chromiumIsAvailable() {
        assumeTrue(PdfToolBox.isAvailable(), "headless chromium not available on this machine");
    }

    private static PDDocument render(String html) throws IOException {
        var bytes = PdfToolBox.generatePDFFromHTML(html);
        assertThat(new String(bytes, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        return Loader.loadPDF(bytes);
    }

    private static String text(PDDocument pdf) throws IOException {
        var stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        return stripper.getText(pdf);
    }

    private static List<String> lines(PDDocument pdf) throws IOException {
        return text(pdf).lines().map(l -> l.trim().replaceAll("\\s+", " ")).filter(l -> !l.isEmpty()).toList();
    }

    private static boolean sameSize(PDRectangle actual, PDRectangle expected) {
        return Math.abs(actual.getWidth() - expected.getWidth()) < 2
                && Math.abs(actual.getHeight() - expected.getHeight()) < 2;
    }

    @Test
    void lenientHtmlIsRendered() throws Exception {
        var html = """
                <!DOCTYPE html>
                <html><head><meta charset="UTF-8"></head>
                <body>
                  <p>Total: 1.234,56 &euro;<br>
                  <p>Café crème – n° 12
                  <table><tr><td>cell 1<td>cell 2</table>
                </body></html>
                """;
        try (var pdf = render(html)) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(1);
            assertThat(text(pdf)).contains("Total: 1.234,56 €", "Café crème – n° 12", "cell 1", "cell 2");
        }
    }

    @Test
    void defaultPageIsA4LikeWkhtmltopdf() throws Exception {
        try (var pdf = render("<p>hello</p>")) {
            assertThat(sameSize(pdf.getPage(0).getMediaBox(), PDRectangle.A4)).isTrue();
        }
    }

    @Test
    void templatePageRulesWin() throws Exception {
        try (var pdf = render("<style>@page { size: A5 landscape; }</style><p>x</p>")) {
            var box = pdf.getPage(0).getMediaBox();
            assertThat(box.getWidth()).isGreaterThan(box.getHeight());
            assertThat(sameSize(box, new PDRectangle(PDRectangle.A5.getHeight(), PDRectangle.A5.getWidth()))).isTrue();
        }
    }

    @Test
    void screenStylesAreUsedLikeWkhtmltopdf() throws Exception {
        // bootstrap & co hide/alter things in @media print: wkhtmltopdf used the screen styles
        var html = """
                <style>@media print { .screen-only { display: none; } } @media screen { .print-only { display: none; } }</style>
                <p class="screen-only">VISIBLE_ON_SCREEN</p><p class="print-only">VISIBLE_IN_PRINT</p>
                """;
        try (var pdf = render(html)) {
            assertThat(text(pdf)).contains("VISIBLE_ON_SCREEN").doesNotContain("VISIBLE_IN_PRINT");
        }
    }

    @Test
    void flexboxLayoutsAreSupported() throws Exception {
        var html = """
                <div style="display: flex; justify-content: space-between"><span>LEFTSIDE</span><span>RIGHTSIDE</span></div>
                <div style="display: grid; grid-template-columns: 1fr 1fr"><div>CELLA</div><div>CELLB</div></div>
                """;
        try (var pdf = render(html)) {
            assertThat(lines(pdf)).anyMatch(l -> l.matches("LEFTSIDE\\s+RIGHTSIDE"))
                    .anyMatch(l -> l.matches("CELLA\\s+CELLB"));
        }
    }

    @Test
    void dataUriImagesAreEmbedded() throws Exception {
        // the logo / QR code are given to the templates as data uris
        var image = new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB);
        var png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);
        var dataUri = "data:image/png;base64," + Base64.getEncoder().encodeToString(png.toByteArray());

        try (var pdf = render("<p>logo</p><img src=\"%s\" width=\"40\">".formatted(dataUri))) {
            var resources = pdf.getPage(0).getResources();
            var images = 0;
            for (var name : resources.getXObjectNames()) {
                if (resources.getXObject(name) instanceof PDImageXObject) {
                    images++;
                }
            }
            assertThat(images).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    void longContentFlowsOnSeveralPages() throws Exception {
        var rows = IntStream.range(0, 200).mapToObj("<tr><td>row %d</td></tr>"::formatted)
                .collect(Collectors.joining());
        try (var pdf = render("<table>" + rows + "</table>")) {
            assertThat(pdf.getNumberOfPages()).isGreaterThan(1);
            assertThat(text(pdf)).contains("row 0", "row 199");
        }
    }

    @Test
    void nullHtmlGivesABlankPage() throws Exception {
        try (var pdf = render(null)) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(1);
            assertThat(text(pdf)).isBlank();
        }
    }

    @Test
    void concurrentRenderingsAreSerializedSafely() throws Exception {
        // invoices / timesheets can be generated from several virtual threads at the same time
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<byte[]>> results = IntStream.range(0, 6)
                    .mapToObj(i -> executor.submit(() -> PdfToolBox.generatePDFFromHTML("<p>doc %d</p>".formatted(i))))
                    .toList();
            for (int i = 0; i < results.size(); i++) {
                try (var pdf = Loader.loadPDF(results.get(i).get())) {
                    assertThat(text(pdf)).contains("doc " + i);
                }
            }
        }
    }

    @Test
    void theBrowserIsRestartedAfterAShutdown() throws Exception {
        PdfToolBox.shutdown();

        try (var pdf = render("<p>after restart</p>")) {
            assertThat(text(pdf)).contains("after restart");
        }
    }
}
