package tech.artcoded.websitev2.filesearch;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Authenticated, read-only proxy to the internal file-search service. */
@RestController
@RequestMapping("/api/file-search")
public class FileSearchProxyController {
    private static final Set<String> QUERY_TYPES = Set.of("termQuery", "regexQuery", "fuzzySearch", "queryParser");
    private final HttpClient client;
    private final URI baseUri;

    public FileSearchProxyController(@Value("${vendors.file-search.base-url:http://file-search:8080}") String baseUrl) {
        this.baseUri = URI.create(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/");
        if (!Set.of("http", "https").contains(this.baseUri.getScheme())
                || this.baseUri.getHost() == null || this.baseUri.getUserInfo() != null
                || this.baseUri.getRawQuery() != null || this.baseUri.getRawFragment() != null) {
            throw new IllegalArgumentException("Invalid file-search base URL");
        }
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> search(@RequestParam("q") String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "per_page", defaultValue = "20") int perPage,
            @RequestParam(name = "query_type", defaultValue = "queryParser") String queryType) {
        if (q.isBlank() || q.length() > 4096 || page < 1 || perPage < 1 || perPage > 100
                || !QUERY_TYPES.contains(queryType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid search parameters");
        }
        return forward("search", Map.of("q", q, "page", Integer.toString(page),
                "per_page", Integer.toString(perPage), "query_type", queryType));
    }

    @GetMapping(value = "/find-all", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> findAll(@RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "per_page", defaultValue = "20") int perPage) {
        if (page < 1 || perPage < 1 || perPage > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination parameters");
        }
        return forward("find-all", Map.of("page", Integer.toString(page), "per_page", Integer.toString(perPage)));
    }

    @GetMapping(value = "/stats", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> stats() {
        return forward("stats", Map.of());
    }

    private ResponseEntity<String> forward(String path, Map<String, String> parameters) {
        StringBuilder target = new StringBuilder(path);
        parameters.forEach((key, value) -> target.append(target.indexOf("?") < 0 ? '?' : '&')
                .append(URLEncoder.encode(key, StandardCharsets.UTF_8)).append('=')
                .append(URLEncoder.encode(value, StandardCharsets.UTF_8)));
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(target.toString()))
                .timeout(Duration.ofSeconds(30)).GET().header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE).build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            // Do not follow redirects or expose upstream 5xx details.
            if (status >= 500 || status >= 300 && status < 400) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "File-search upstream error");
            }
            return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "File-search request interrupted", e);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "File-search unavailable", e);
        }
    }
}
