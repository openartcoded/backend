package tech.artcoded.websitev2.filesearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.sun.net.httpserver.HttpServer;

import tech.artcoded.websitev2.upload.CorrelationLinkService;
import tech.artcoded.websitev2.upload.FileUpload;
import tech.artcoded.websitev2.upload.IFileUploadService;

class FileSearchProxyControllerTest {
    private static final String ZIP_ID = "0c1d2e3f-1234-4abc-8def-0123456789ab";
    private static final String PDF_ID = "01a1c466-6813-7242-853d-8212b7bfb28e";

    private HttpServer server;
    private final AtomicReference<String> lastQuery = new AtomicReference<>();
    private final AtomicReference<Integer> status = new AtomicReference<>(200);
    private final AtomicReference<String> body = new AtomicReference<>("");
    private final IFileUploadService uploads = mock(IFileUploadService.class);
    private final CorrelationLinkService links = mock(CorrelationLinkService.class);
    private FileSearchProxyController controller;

    @BeforeEach
    void startFileSearch() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v2/search", exchange -> {
            lastQuery.set(URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8));
            var bytes = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status.get(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        controller = new FileSearchProxyController("http://127.0.0.1:" + server.getAddress().getPort(), uploads,
                links);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private FileSearchResult search(String q) {
        return controller.searchV2(q, "smart", null, null, null, null, null, null, 0, 20, true, null);
    }

    @Test
    void theUploadOfEachHitIsResolved() {
        body.set("""
                {"hits": [
                  {"score": 7.5, "source_path": "/app/state/extracted/x/facture.pdf",
                   "origin_path": "/uploads/%1$s.zip", "file_name": "facture.pdf",
                   "origin_file_name": "%1$s.zip", "kind": "pdf", "extension": "pdf", "sheet_name": null,
                   "positions": ["chunk 1"], "context": [], "content": "Facture octobre",
                   "snippet_html": "<b>Facture</b> octobre", "modified_at": "2026-10-31", "matches": 2},
                  {"score": 3.1, "source_path": "/uploads/%2$s.pdf", "origin_path": "/uploads/%2$s.pdf",
                   "file_name": "%2$s.pdf", "origin_file_name": "%2$s.pdf", "kind": "pdf", "extension": "pdf",
                   "positions": ["chunk 3"], "context": [], "content": "x", "snippet_html": "x",
                   "modified_at": null, "matches": 1, "unknown_field": true},
                  {"score": 1.0, "file_name": "notes.txt", "origin_file_name": "notes.txt", "kind": "text",
                   "extension": "txt", "positions": [], "context": [], "content": "y", "snippet_html": "y",
                   "matches": 1}
                ], "page": 0, "per_page": 20, "has_more": true, "total_matches": 9, "took_ms": 4}
                """.formatted(ZIP_ID, PDF_ID));
        when(uploads.findAll(anyCollection())).thenReturn(List.of(
                FileUpload.builder().id(ZIP_ID).originalFilename("factures-2026.zip").correlationId("fee-1")
                        .build(),
                FileUpload.builder().id(PDF_ID).originalFilename("receipt.pdf").build()));
        when(links.getLabels(List.of("fee-1"))).thenReturn(Map.of("fee-1", "Expense 'Hosting'"));

        var result = search("facture octobre");

        assertThat(result.hasMore()).isTrue();
        assertThat(result.totalMatches()).isEqualTo(9);
        var extracted = result.hits().get(0);
        assertThat(extracted.extracted()).isTrue();
        assertThat(extracted.fileName()).isEqualTo("facture.pdf");
        assertThat(extracted.upload().originalFilename()).isEqualTo("factures-2026.zip");
        assertThat(extracted.upload().correlationLabel()).isEqualTo("Expense 'Hosting'");
        assertThat(extracted.matches()).isEqualTo(2);
        assertThat(extracted.snippetHtml()).isEqualTo("<b>Facture</b> octobre");
        var pdf = result.hits().get(1);
        assertThat(pdf.extracted()).isFalse();
        assertThat(pdf.upload().id()).isEqualTo(PDF_ID);
        assertThat(pdf.upload().correlationLabel()).isNull();
        assertThat(result.hits().get(2).upload()).as("not an upload").isNull();
        assertThat(lastQuery.get()).contains("q=facture octobre").contains("mode=smart")
                .contains("group_by_file=true").doesNotContain("kinds");
    }

    @Test
    void filtersAreValidatedThenForwarded() {
        body.set("""
                {"hits": [], "page": 0, "per_page": 50, "has_more": false, "total_matches": 0, "took_ms": 1}
                """);
        var result = controller.searchV2("", "fuzzy", "content, sheet", "PDF,spreadsheet", ".xlsx", "2026-01-01",
                "2026-12-31", 1, 0, 50, false, 300);
        assertThat(result.hits()).isEmpty();
        assertThat(lastQuery.get()).contains("fields=content,sheet").contains("kinds=pdf,spreadsheet")
                .contains("extensions=xlsx").contains("modified_from=2026-01-01")
                .contains("modified_to=2026-12-31").contains("fuzziness=1").contains("group_by_file=false")
                .contains("snippet_chars=300");
        verify(uploads, never()).findAll(anyCollection());
    }

    @Test
    void aWrongRequestNeverReachesTheFileSearch() {
        assertThatThrownBy(() -> search("  ")).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("a text or a filter is required");
        assertThatThrownBy(
                () -> controller.searchV2("x", "magic", null, "video", "x.y", "31/12/2026", null, 3, -1, 500, true, 5))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("mode")
                .hasMessageContaining("kinds").hasMessageContaining("extensions")
                .hasMessageContaining("yyyy-mm-dd").hasMessageContaining("fuzziness")
                .hasMessageContaining("per_page").hasMessageContaining("snippet_chars");
        assertThat(lastQuery.get()).isNull();
    }

    @Test
    void anErrorOfTheFileSearchIsABadRequestWithItsMessage() {
        status.set(400);
        body.set("{\"error\": \"invalid regex: unclosed group\"}");
        assertThatThrownBy(() -> controller.searchV2("(", "regex", null, null, null, null, null, null, 0, 20, true,
                null)).isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getReason()).isEqualTo("invalid regex: unclosed group");
                });
        status.set(500);
        body.set("boom");
        assertThatThrownBy(() -> search("x")).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void theReasonOfARefusalIsInTheBody() {
        var response = controller.refused(new ResponseStatusException(HttpStatus.BAD_REQUEST, "mode: one of ..."));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "mode: one of ...");
    }

    @Test
    void uploadIds() {
        assertThat(FileSearchProxyController.uploadId(PDF_ID + ".pdf")).contains(PDF_ID);
        assertThat(FileSearchProxyController.uploadId(ZIP_ID.toUpperCase())).contains(ZIP_ID);
        assertThat(FileSearchProxyController.uploadId("report.pdf")).isEmpty();
        assertThat(FileSearchProxyController.uploadId(PDF_ID + ".tar/x")).isEmpty();
        assertThat(FileSearchProxyController.uploadId(null)).isEmpty();
    }
}
