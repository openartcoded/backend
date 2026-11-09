package tech.artcoded.websitev2.rest.util;

/** Test helper: exposes {@link PdfToolBox#isAvailable()} to tests of other packages. */
public final class PdfToolBoxAccess {
    private PdfToolBoxAccess() {
    }

    public static boolean chromiumAvailable() {
        return PdfToolBox.isAvailable();
    }
}
