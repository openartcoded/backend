package tech.artcoded.websitev2.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.MultipartAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartHttpServletRequest;

/**
 * The embedded server (tomcat, formerly undertow) with the application's web.yml: requests run on virtual threads and
 * the limits where tomcat is stricter than undertow are relaxed.
 */
@SpringBootTest(classes = TomcatServerIntegrationTest.App.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=classpath:config/web.yml", "spring.threads.virtual.enabled=true" })
class TomcatServerIntegrationTest {

    @TempDir
    static Path basePath;

    @DynamicPropertySource
    static void basePath(DynamicPropertyRegistry registry) {
        // a multipart location that doesn't exist yet: it must be created at startup
        registry.add("application.basePath", () -> basePath.resolve("not-yet-created").toString());
    }

    @SpringBootConfiguration
    @ImportAutoConfiguration({ ServletWebServerFactoryAutoConfiguration.class,
            DispatcherServletAutoConfiguration.class, WebMvcAutoConfiguration.class,
            HttpMessageConvertersAutoConfiguration.class, JacksonAutoConfiguration.class,
            MultipartAutoConfiguration.class, PropertyPlaceholderAutoConfiguration.class })
    @Import({ MultipartLocationConfig.class, TestController.class })
    static class App {
    }

    @RestController
    static class TestController {
        @GetMapping("/echo")
        Map<String, Object> echo(@RequestParam("q") String q) {
            return Map.of("q", q, "virtual", Thread.currentThread().isVirtual());
        }

        @PostMapping(value = "/parts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        int parts(MultipartHttpServletRequest request) {
            return request.getParameterMap().size() + request.getFileMap().size();
        }

        @PostMapping(value = "/form", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
        int form(@RequestParam("content") String content) {
            return content.length();
        }
    }

    @LocalServerPort
    int port;
    @Autowired
    ServletWebServerApplicationContext context;

    private RestClient client() {
        return RestClient.create("http://localhost:" + port);
    }

    @Test
    void theServerIsTomcatOnVirtualThreads() {
        assertThat(context.getWebServer()).isInstanceOf(TomcatWebServer.class);
        var body = client().get().uri("/echo?q=hello").retrieve().body(Map.class);
        assertThat(body).containsEntry("virtual", true);
    }

    @Test
    void squareBracketsAreAcceptedUnencodedInTheQuery() throws Exception {
        // like axios does for a label such as [INTERNET]; a raw socket because java URIs refuse [ in a query
        try (var socket = new Socket("localhost", port)) {
            OutputStream out = socket.getOutputStream();
            out.write(("GET /echo?q=[INTERNET] HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            out.flush();
            var response = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
                    .lines().toList();
            assertThat(response.getFirst()).contains("200");
            assertThat(String.join("\n", response)).contains("[INTERNET]");
        }
    }

    @Test
    void aMultipartRequestCanHaveManyParts() {
        var form = new LinkedMultiValueMap<String, Object>();
        for (int i = 0; i < 120; i++) {
            form.add("field" + i, "value" + i);
        }
        var parts = client().post().uri("/parts").contentType(MediaType.MULTIPART_FORM_DATA).body(form).retrieve()
                .body(Integer.class);
        assertThat(parts).isEqualTo(120);
        assertThat(Files.isDirectory(basePath.resolve("not-yet-created/multipart/upload"))).isTrue();
    }

    @Test
    void aUrlEncodedFormCanBeBiggerThan2MB() {
        var content = "x".repeat(3 * 1024 * 1024);
        var form = new LinkedMultiValueMap<String, String>();
        form.add("content", content);
        var length = client().post().uri("/form").contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                .retrieve().body(Integer.class);
        assertThat(length).isEqualTo(content.length());
    }
}
