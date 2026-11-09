package tech.artcoded.websitev2.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tech.artcoded.websitev2.rest.util.PdfToolBox;
import tech.artcoded.websitev2.rest.util.PdfToolBoxAccess;
import tech.artcoded.websitev2.utils.func.CheckedFunction;

/**
 * GraalJS ({@code org.graalvm.polyglot:js}, formerly js-community) running a user script that renders a PDF (headless
 * chromium), with
 * the same context options and binding as {@link ScriptProcessorFactory}.
 */
class ScriptPdfIntegrationTest {
    private Context ctx;

    @BeforeEach
    void createContext() {
        ctx = Context.newBuilder("js").allowHostAccess(HostAccess.ALL).allowHostClassLookup(_ -> true)
                .option("engine.WarnInterpreterOnly", "false").option("js.ecmascript-version", "2022").build();
        ctx.getBindings("js").putMember("generatePdf", CheckedFunction.toFunction(PdfToolBox::generatePDFFromHTML));
    }

    @AfterEach
    void close() {
        ctx.close();
    }

    @Test
    void scriptsCanRenderPdfs() throws Exception {
        assumeTrue(PdfToolBoxAccess.chromiumAvailable(), "headless chromium not available on this machine");
        var bytes = ctx.eval("js", """
                const rows = ['train', 'hotel'].map((e, i) => `<tr><td>${i + 1}</td><td>${e}</td></tr>`).join('');
                generatePdf.apply(`<h1>Expenses</h1><table>${rows}</table>`);
                """).as(byte[].class);

        assertThat(new String(bytes, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        try (var pdf = Loader.loadPDF(bytes)) {
            assertThat(new PDFTextStripper().getText(pdf)).contains("Expenses", "train", "hotel");
        }
    }

    @Test
    void modernJavascriptIsSupported() {
        var result = ctx.eval("js", """
                const total = [{ price: 10.5 }, { price: 2 }].reduce((acc, { price }) => acc + price, 0);
                const label = null ?? 'default';
                `${label}:${total}`;
                """).asString();

        assertThat(result).isEqualTo("default:12.5");
    }

    @Test
    void scriptErrorsSurfaceAsPolyglotExceptions() {
        assertThatThrownBy(() -> ctx.eval("js", "throw new Error('boom from script')"))
                .isInstanceOf(PolyglotException.class).hasMessageContaining("boom from script");
    }
}
