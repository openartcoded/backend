package tech.artcoded.websitev2.rest.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Rendering options, no browser needed. */
class PdfToolBoxOptionsTest {

    @Test
    void defaultsMimicWkhtmltopdf() {
        var options = PdfToolBox.pdfOptions();

        assertThat(options.format).isEqualTo("A4");
        assertThat(options.printBackground).isTrue();
        assertThat(options.preferCSSPageSize).isTrue();
        assertThat(options.margin.top).isEqualTo("10mm");
        assertThat(options.margin.left).isEqualTo("10mm");
        assertThat(PdfToolBox.scale()).isEqualTo(1.0);
        assertThat(PdfToolBox.timeoutMs()).isEqualTo(30_000d);
    }
}
