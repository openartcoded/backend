package tech.artcoded.websitev2.rest.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The docker images install chromium in a cached layer with the Playwright CLI, before the application jar is copied.
 * That CLI version (ARG PLAYWRIGHT_VERSION) must be the one the application uses, otherwise the installed browser does
 * not match and pdf rendering fails at runtime.
 */
class DockerfilePlaywrightVersionTest {
    /** Tests run from the artcoded module, the Dockerfiles are at the repository root. */
    private static final Path ROOT = Path.of("..");

    private static String find(Path file, String regex) throws Exception {
        var matcher = Pattern.compile(regex).matcher(Files.readString(file));
        assertThat(matcher.find()).as("%s in %s", regex, file).isTrue();
        return matcher.group(1);
    }

    @ParameterizedTest
    @ValueSource(strings = { "Dockerfile", "Dockerfile.github" })
    void dockerfileInstallsTheChromiumOfThePlaywrightVersionUsedByTheApp(String dockerfile) throws Exception {
        var pomVersion = find(ROOT.resolve("pom.xml"), "<playwright.version>([^<]+)</playwright.version>");
        var dockerVersion = find(ROOT.resolve(dockerfile), "(?m)^ARG PLAYWRIGHT_VERSION=(\\S+)$");

        assertThat(dockerVersion).isEqualTo(pomVersion);
    }

    @ParameterizedTest
    @ValueSource(strings = { "Dockerfile", "Dockerfile.github" })
    void chromiumLayerComesBeforeTheJarSoItStaysCached(String dockerfile) throws Exception {
        var content = Files.readString(ROOT.resolve(dockerfile));
        var chromium = content.indexOf("com.microsoft.playwright.CLI install");
        var jar = content.indexOf("api-backend.jar ./api-backend.jar");

        assertThat(chromium).isPositive();
        assertThat(jar).isGreaterThan(chromium);
        assertThat(content).contains("ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1");
    }
}
