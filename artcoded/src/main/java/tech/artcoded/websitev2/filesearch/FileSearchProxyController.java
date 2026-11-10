package tech.artcoded.websitev2.filesearch;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import tech.artcoded.websitev2.upload.CorrelationLinkService;
import tech.artcoded.websitev2.upload.FileUpload;
import tech.artcoded.websitev2.upload.IFileUploadService;
import tech.artcoded.websitev2.utils.helper.Json;

/** Authenticated, read-only proxy to the internal file-search service. */
@RestController
@RequestMapping("/api/file-search")
public class FileSearchProxyController {
    private static final Set<String> QUERY_TYPES = Set.of("termQuery", "regexQuery", "fuzzySearch", "queryParser");
    static final Set<String> MODES = Set.of("smart", "exact", "prefix", "fuzzy", "phrase", "regex", "query");
    static final Set<String> FIELDS = Set.of("content", "file_name", "sheet", "context");
    static final Set<String> KINDS = Set.of("pdf", "spreadsheet", "csv", "document", "image", "text");
    private static final Pattern EXTENSION = Pattern.compile("[a-z0-9]{1,10}");
    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    /** an upload is stored as "{id}.{extension}": its id, from the name of the file indexed */
    private static final Pattern UPLOAD_FILE_NAME = Pattern.compile(
            "^([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})(?:\\.[^./]+)?$", Pattern.CASE_INSENSITIVE);

    private final HttpClient client;
    private final URI baseUri;
    private final IFileUploadService uploadService;
    private final CorrelationLinkService linkService;

    public FileSearchProxyController(@Value("${vendors.file-search.base-url:http://file-search:8080}") String baseUrl,
            IFileUploadService uploadService, CorrelationLinkService linkService) {
        this.uploadService = uploadService;
        this.linkService = linkService;
        this.baseUri = URI.create(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/");
        if (!Set.of("http", "https").contains(this.baseUri.getScheme()) || this.baseUri.getHost() == null
                || this.baseUri.getUserInfo() != null || this.baseUri.getRawQuery() != null
                || this.baseUri.getRawFragment() != null) {
            throw new IllegalArgumentException("Invalid file-search base URL");
        }
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> search(@RequestParam("q") String q, @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "per_page", defaultValue = "20") int perPage,
            @RequestParam(name = "query_type", defaultValue = "queryParser") String queryType) {
        if (q.isBlank() || q.length() > 4096 || page < 0 || perPage < 1 || perPage > 100
                || !QUERY_TYPES.contains(queryType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid search parameters");
        }
        return forward("search", Map.of("q", q, "page", Integer.toString(page), "per_page", Integer.toString(perPage),
                "query_type", queryType));
    }

    /**
     * The search with modes, fields and filters (file search /v2/search), the upload of each hit resolved.
     *
     * @param mode
     *            smart (default), exact, prefix, fuzzy, phrase, regex or query
     * @param fields
     *            comma separated: content, file_name, sheet, context. Default: all
     * @param kinds
     *            comma separated: pdf, spreadsheet, csv, document, image, text
     * @param modifiedFrom
     *            yyyy-mm-dd
     * @param groupByFile
     *            true: the best match of each file, false: every matching row / chunk
     */
    @GetMapping(value = "/v2/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public FileSearchResult searchV2(@RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "smart") String mode, @RequestParam(required = false) String fields,
            @RequestParam(required = false) String kinds, @RequestParam(required = false) String extensions,
            @RequestParam(name = "modified_from", required = false) String modifiedFrom,
            @RequestParam(name = "modified_to", required = false) String modifiedTo,
            @RequestParam(required = false) Integer fuzziness, @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "per_page", defaultValue = "20") int perPage,
            @RequestParam(name = "group_by_file", defaultValue = "true") boolean groupByFile,
            @RequestParam(name = "snippet_chars", required = false) Integer snippetChars) {
        var kindList = list(kinds);
        var extensionList = list(extensions).stream().map(e -> e.startsWith(".") ? e.substring(1) : e).toList();
        var fieldList = list(fields);
        List<String> errors = new ArrayList<>();
        if (q.length() > 4096) {
            errors.add("q: at most 4096 characters");
        }
        if (!MODES.contains(mode)) {
            errors.add("mode: one of " + String.join(", ", MODES));
        }
        if (!FIELDS.containsAll(fieldList)) {
            errors.add("fields: among " + String.join(", ", FIELDS));
        }
        if (!KINDS.containsAll(kindList)) {
            errors.add("kinds: among " + String.join(", ", KINDS));
        }
        if (!extensionList.stream().allMatch(e -> EXTENSION.matcher(e).matches())) {
            errors.add("extensions: letters and digits");
        }
        if (Optional.ofNullable(modifiedFrom).filter(d -> !d.isBlank()).filter(d -> !DATE.matcher(d).matches())
                .isPresent()
                || Optional.ofNullable(modifiedTo).filter(d -> !d.isBlank()).filter(d -> !DATE.matcher(d).matches())
                        .isPresent()) {
            errors.add("modified_from / modified_to: yyyy-mm-dd");
        }
        if (fuzziness != null && (fuzziness < 0 || fuzziness > 2)) {
            errors.add("fuzziness: 0, 1 or 2");
        }
        if (page < 0 || page > 1000 || perPage < 1 || perPage > 100) {
            errors.add("page: 0 to 1000, per_page: 1 to 100");
        }
        if (snippetChars != null && (snippetChars < 40 || snippetChars > 1000)) {
            errors.add("snippet_chars: 40 to 1000");
        }
        boolean filtered = !kindList.isEmpty() || !extensionList.isEmpty()
                || Optional.ofNullable(modifiedFrom).filter(d -> !d.isBlank()).isPresent()
                || Optional.ofNullable(modifiedTo).filter(d -> !d.isBlank()).isPresent();
        if (q.isBlank() && !filtered) {
            errors.add("a text or a filter is required");
        }
        if (!errors.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("; ", errors));
        }

        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("q", q);
        parameters.put("mode", mode);
        parameters.put("page", Integer.toString(page));
        parameters.put("per_page", Integer.toString(perPage));
        parameters.put("group_by_file", Boolean.toString(groupByFile));
        if (!fieldList.isEmpty()) {
            parameters.put("fields", String.join(",", fieldList));
        }
        if (!kindList.isEmpty()) {
            parameters.put("kinds", String.join(",", kindList));
        }
        if (!extensionList.isEmpty()) {
            parameters.put("extensions", String.join(",", extensionList));
        }
        Optional.ofNullable(modifiedFrom).filter(d -> !d.isBlank()).ifPresent(d -> parameters.put("modified_from", d));
        Optional.ofNullable(modifiedTo).filter(d -> !d.isBlank()).ifPresent(d -> parameters.put("modified_to", d));
        Optional.ofNullable(fuzziness).ifPresent(f -> parameters.put("fuzziness", f.toString()));
        Optional.ofNullable(snippetChars).ifPresent(c -> parameters.put("snippet_chars", c.toString()));

        HttpResponse<String> response = send("v2/search", parameters);
        if (response.statusCode() == HttpStatus.BAD_REQUEST.value()) {
            String message;
            try {
                message = Json.MAPPER.readValue(response.body(), UpstreamError.class).error();
            } catch (RuntimeException e) {
                message = "Invalid search parameters";
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        if (response.statusCode() != HttpStatus.OK.value()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "File-search upstream error");
        }
        UpstreamResponse upstream;
        try {
            upstream = Json.MAPPER.readValue(response.body(), UpstreamResponse.class);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unexpected response from file-search", e);
        }
        return resolveUploads(upstream);
    }

    /** the id of the upload stored as this file name, empty when it isn't an upload */
    static Optional<String> uploadId(String fileName) {
        return Optional.ofNullable(fileName).map(UPLOAD_FILE_NAME::matcher).filter(m -> m.matches())
                .map(m -> m.group(1).toLowerCase());
    }

    FileSearchResult resolveUploads(UpstreamResponse upstream) {
        var hits = Optional.ofNullable(upstream.hits()).orElseGet(List::of);
        // the upload of an extracted file is its archive
        var ids = hits.stream().map(h -> uploadId(h.originFileName()).or(() -> uploadId(h.fileName())))
                .flatMap(Optional::stream).distinct().toList();
        Map<String, FileUpload> uploads = ids.isEmpty() ? Map.of()
                : uploadService.findAll(ids).stream()
                        .collect(Collectors.toMap(FileUpload::getId, Function.identity(), (a, b) -> a));
        var correlationIds = uploads.values().stream().map(FileUpload::getCorrelationId).filter(Objects::nonNull)
                .distinct().toList();
        Map<String, String> labels = linkService.getLabels(correlationIds);
        return new FileSearchResult(hits.stream().map(h -> {
            var upload = uploadId(h.originFileName()).or(() -> uploadId(h.fileName())).map(uploads::get)
                    .map(u -> new FileSearchResult.Upload(u.getId(), u.getOriginalFilename(), u.getContentType(),
                            u.getSize(), u.getCreationDate(), u.getCorrelationId(),
                            u.getCorrelationId() == null ? null : labels.get(u.getCorrelationId())))
                    .orElse(null);
            boolean extracted = h.originFileName() != null && !h.originFileName().equals(h.fileName());
            return new FileSearchResult.Hit(h.score(), h.fileName(), h.originFileName(), extracted, h.kind(),
                    h.extension(), h.sheetName(), Optional.ofNullable(h.positions()).orElseGet(List::of),
                    Optional.ofNullable(h.context()).orElseGet(List::of), h.content(), h.snippetHtml(), h.modifiedAt(),
                    h.matches(), upload);
        }).toList(), upstream.page(), upstream.perPage(), upstream.hasMore(), upstream.totalMatches(),
                upstream.tookMs());
    }

    private static List<String> list(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split(",")).map(String::trim).map(String::toLowerCase)
                .filter(v -> !v.isEmpty()).distinct().toList();
    }

    /** the response of the file search /v2/search (the paths of the files are not read) */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record UpstreamResponse(List<UpstreamHit> hits, int page, @JsonProperty("per_page") int perPage,
            @JsonProperty("has_more") boolean hasMore, @JsonProperty("total_matches") long totalMatches,
            @JsonProperty("took_ms") long tookMs) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UpstreamHit(double score, @JsonProperty("file_name") String fileName,
            @JsonProperty("origin_file_name") String originFileName, String kind, String extension,
            @JsonProperty("sheet_name") String sheetName, List<String> positions, List<String> context, String content,
            @JsonProperty("snippet_html") String snippetHtml, @JsonProperty("modified_at") String modifiedAt,
            int matches) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UpstreamError(String error) {
    }

    /**
     * The reason of a refused search in the body ({"error": "..."}): the backoffice shows it (invalid regex, unknown
     * kind...). Spring doesn't include it in its error body.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> refused(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("error", Optional.ofNullable(e.getReason()).orElse("File-search error")));
    }

    @GetMapping(value = "/find-all", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> findAll(@RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "per_page", defaultValue = "20") int perPage) {
        if (page < 0 || perPage < 1 || perPage > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination parameters");
        }
        return forward("find-all", Map.of("page", Integer.toString(page), "per_page", Integer.toString(perPage)));
    }

    @GetMapping(value = "/stats", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> stats() {
        return forward("stats", Map.of());
    }

    private ResponseEntity<String> forward(String path, Map<String, String> parameters) {
        HttpResponse<String> response = send(path, parameters);
        int status = response.statusCode();
        // Do not follow redirects or expose upstream 5xx details.
        if (status >= 500 || status >= 300 && status < 400) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "File-search upstream error");
        }
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(response.body());
    }

    private HttpResponse<String> send(String path, Map<String, String> parameters) {
        StringBuilder target = new StringBuilder(path);
        parameters.forEach((key, value) -> target.append(target.indexOf("?") < 0 ? '?' : '&')
                .append(URLEncoder.encode(key, StandardCharsets.UTF_8)).append('=')
                .append(URLEncoder.encode(value, StandardCharsets.UTF_8)));
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(target.toString())).timeout(Duration.ofSeconds(30))
                .GET().header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE).build();
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "File-search request interrupted", e);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "File-search unavailable", e);
        }
    }
}
