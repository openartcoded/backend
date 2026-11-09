package tech.artcoded.websitev2.rest.util;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.Margin;
import com.microsoft.playwright.options.Media;
import com.microsoft.playwright.options.WaitUntilState;

import lombok.extern.slf4j.Slf4j;

/**
 * HTML to PDF with headless Chromium (driven by Playwright). Replaces the wkhtmltopdf executable (archived, old QtWebKit
 * engine): Chromium renders modern CSS (flexbox, grid, web fonts) like a browser does.
 * <p>
 * Defaults mimic wkhtmltopdf so the existing templates look the same: A4, 10mm margins, backgrounds printed and the
 * <b>screen</b> media type (wkhtmltopdf ignores {@code @media print} by default, Chromium would apply it).
 * <p>
 * Configuration (environment variables):
 * <ul>
 * <li>{@value #EXECUTABLE_ENV}: path of a chromium/chrome executable to use instead of the one installed by
 * Playwright</li>
 * <li>{@value #SCALE_ENV}: rendering scale (0.1 - 2, default 1)</li>
 * <li>{@value #TIMEOUT_ENV}: max time in ms to load the page and its resources (default 30000)</li>
 * <li>{@code PLAYWRIGHT_BROWSERS_PATH} / {@code PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD}: standard Playwright variables (see
 * the Dockerfiles)</li>
 * </ul>
 * Playwright objects are not thread-safe: renderings are serialized, the browser is started once and reused.
 */
@Slf4j
public final class PdfToolBox {
    static final String EXECUTABLE_ENV = "PDF_CHROMIUM_EXECUTABLE";
    static final String SCALE_ENV = "PDF_SCALE";
    static final String TIMEOUT_ENV = "PDF_TIMEOUT_MS";

    static final String DEFAULT_MARGIN = "10mm";
    static final String DEFAULT_FORMAT = "A4";

    private static final ReentrantLock LOCK = new ReentrantLock();
    private static Playwright playwright;
    private static Browser browser;

    static {
        Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().unstarted(PdfToolBox::shutdown));
    }

    private PdfToolBox() {
    }

    public static byte[] generatePDFFromHTML(String html) throws IOException {
        LOCK.lock();
        try (var context = browser().newContext(); var page = context.newPage()) {
            // like wkhtmltopdf: screen styles, not the print ones
            page.emulateMedia(new Page.EmulateMediaOptions().setMedia(Media.SCREEN));
            // "load" waits for stylesheets and images (e.g. bootstrap from the CDN, data uri logos)
            page.setContent(html == null ? "" : html,
                    new Page.SetContentOptions().setWaitUntil(WaitUntilState.LOAD).setTimeout(timeoutMs()));
            // web fonts are loaded lazily, after the load event
            page.evaluate("() => document.fonts.ready.then(() => true)");
            return page.pdf(pdfOptions());
        } catch (PlaywrightException e) {
            // the browser may have crashed or been killed: start a fresh one next time
            shutdown();
            throw new IOException("could not render the pdf with chromium: " + e.getMessage(), e);
        } finally {
            LOCK.unlock();
        }
    }

    static Page.PdfOptions pdfOptions() {
        return new Page.PdfOptions().setFormat(DEFAULT_FORMAT).setPrintBackground(true)
                // a @page rule in the template (size, margin) wins over the defaults
                .setPreferCSSPageSize(true).setScale(scale())
                .setMargin(new Margin().setTop(DEFAULT_MARGIN).setRight(DEFAULT_MARGIN).setBottom(DEFAULT_MARGIN)
                        .setLeft(DEFAULT_MARGIN));
    }

    static double scale() {
        return env(SCALE_ENV).map(Double::parseDouble).map(s -> Math.clamp(s, 0.1, 2.0)).orElse(1.0);
    }

    static double timeoutMs() {
        return env(TIMEOUT_ENV).map(Double::parseDouble).filter(t -> t > 0).orElse(30_000d);
    }

    private static Optional<String> env(String name) {
        return Optional.ofNullable(System.getenv(name)).map(String::trim).filter(s -> !s.isEmpty());
    }

    private static Browser browser() {
        if (browser == null || !browser.isConnected()) {
            shutdown();
            log.info("starting headless chromium for pdf rendering");
            playwright = Playwright.create();
            var options = new BrowserType.LaunchOptions().setHeadless(true)
                    // shared memory is often tiny in containers
                    .setArgs(List.of("--disable-dev-shm-usage", "--disable-gpu", "--font-render-hinting=none"));
            env(EXECUTABLE_ENV).map(Path::of).ifPresent(options::setExecutablePath);
            browser = playwright.chromium().launch(options);
        }
        return browser;
    }

    /** Stops the browser (it is restarted on the next rendering). */
    static void shutdown() {
        LOCK.lock();
        try {
            if (playwright != null) {
                playwright.close(); // closes the browser too
            }
        } catch (Exception e) {
            log.warn("could not close playwright cleanly", e);
        } finally {
            playwright = null;
            browser = null;
            LOCK.unlock();
        }
    }

    /** True when chromium can be started on this machine (used by the tests to skip when it is not installed). */
    static boolean isAvailable() {
        try {
            generatePDFFromHTML("<p>ping</p>");
            return true;
        } catch (Exception | Error e) {
            log.warn("headless chromium not available: {}", e.getMessage());
            return false;
        }
    }
}
