package tech.artcoded.websitev2.template;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import lombok.SneakyThrows;
import tech.artcoded.websitev2.rest.util.RestUtil;
import tech.artcoded.websitev2.utils.helper.Json;

/**
 * Single place to manage every template (admin only, like every route not opened in the security config). Per type: any
 * number of v1 and v2 templates, one default (the one rendered).
 */
@RestController
@RequestMapping("/api/template")
public class OaTemplateController {
    private final OaTemplateService templateService;
    private final OaTemplateRenderer templateRenderer;

    public OaTemplateController(OaTemplateService templateService, OaTemplateRenderer templateRenderer) {
        this.templateService = templateService;
        this.templateRenderer = templateRenderer;
    }

    /** a type, what it renders, and the extension a v2 template file of this type gets */
    public record TemplateTypeInfo(OaTemplateType type, OaTemplateType.Output output, String extension) {
    }

    @GetMapping("/types")
    public List<TemplateTypeInfo> types() {
        return Arrays.stream(OaTemplateType.values()).map(t -> new TemplateTypeInfo(t, t.output(), t.extension()))
                .toList();
    }

    @GetMapping("/list")
    public List<OaTemplate> list(@RequestParam(value = "type", required = false) OaTemplateType type) {
        return templateService.list(Optional.ofNullable(type));
    }

    @GetMapping("/find-by-id")
    public ResponseEntity<OaTemplate> findById(@RequestParam("id") String id) {
        return templateService.findById(id).map(ResponseEntity::ok).orElseGet(ResponseEntity.notFound()::build);
    }

    /** the default template of a type: the one rendered */
    @GetMapping("/default")
    public ResponseEntity<OaTemplate> getDefault(@RequestParam("type") OaTemplateType type) {
        return templateService.getDefault(type).map(ResponseEntity::ok).orElseGet(ResponseEntity.notFound()::build);
    }

    /** makes this template the default of its type (the previous default stops being one) */
    @PostMapping("/default")
    public ResponseEntity<OaTemplate> setDefault(@RequestParam("id") String id) {
        return templateService.setDefault(id).map(ResponseEntity::ok).orElseGet(ResponseEntity.notFound()::build);
    }

    /**
     * Adds a template, or overwrites the template with this id. v2 (jinja, file service) unless v1=true (FreeMarker).
     * default=true makes it the default of its type (the first template of a type always is).
     */
    @PostMapping(value = "/save", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public OaTemplate save(@RequestParam(value = "id", required = false) String id, @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("type") OaTemplateType type, @RequestParam(value = "v1", defaultValue = "false") boolean v1,
            @RequestParam(value = "default", defaultValue = "false") boolean makeDefault,
            @RequestPart(value = "template", required = false) MultipartFile template) {
        return templateService.save(id, name, description, type, v1, makeDefault, template);
    }

    /** refused for the default template */
    @DeleteMapping
    public void delete(@RequestParam("id") String id) {
        templateService.delete(id);
    }

    /** the template file, to edit it before overwriting it */
    @GetMapping("/download")
    public ResponseEntity<ByteArrayResource> download(@RequestParam("id") String id) {
        return templateService.findById(id)
                .map(t -> RestUtil.transformToByteArrayResource(
                        t.getName() + (t.getVersion() == OaTemplateVersion.V1 ? ".ftl" : t.getType().extension()),
                        t.getType().isXml() ? MediaType.APPLICATION_XML_VALUE : MediaType.TEXT_PLAIN_VALUE,
                        templateService.source(t)))
                .orElseGet(ResponseEntity.notFound()::build);
    }

    /** the variables a v2 template reads (e.g. invoice.total), to help writing it. Empty for a v1 template. */
    @GetMapping("/variables")
    public ResponseEntity<List<String>> variables(@RequestParam("id") String id) {
        return templateService.findById(id).map(templateService::variables).map(ResponseEntity::ok)
                .orElseGet(ResponseEntity.notFound()::build);
    }

    /**
     * A context to preview a jinja template: the variables its source reads, with mock values. The source is the
     * uploaded one (not saved yet), or the one of the template with this id.
     */
    @PostMapping(value = "/mock-context", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE,
            MediaType.APPLICATION_FORM_URLENCODED_VALUE })
    public Map<String, Object> mockContext(@RequestParam(value = "id", required = false) String id,
            @RequestPart(value = "template", required = false) MultipartFile template) {
        return JinjaMockContext.of(previewSource(id, template));
    }

    /**
     * Renders a template without saving it: the uploaded source (the one being edited), or the template with this id.
     * The context is a json object (the model of the template), a mock built from the source when omitted. Answers what
     * the template renders: a pdf, an xml or a text.
     */
    @PostMapping(value = "/preview", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE,
            MediaType.APPLICATION_FORM_URLENCODED_VALUE })
    @SuppressWarnings("unchecked")
    public ResponseEntity<ByteArrayResource> preview(@RequestParam(value = "id", required = false) String id,
            @RequestParam(value = "type", required = false) OaTemplateType type,
            @RequestParam(value = "v1", required = false) Boolean v1,
            @RequestParam(value = "context", required = false) String context,
            @RequestPart(value = "template", required = false) MultipartFile template) {
        var saved = templateService.findById(id);
        if (StringUtils.isNotBlank(id) && saved.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var previewType = Optional.ofNullable(type).or(() -> saved.map(OaTemplate::getType))
                .orElseThrow(() -> new IllegalArgumentException("the type of the template is required"));
        boolean previewV1 = Optional.ofNullable(v1)
                .orElseGet(() -> saved.map(t -> t.getVersion() == OaTemplateVersion.V1).orElse(false));
        String source = previewSource(id, template);
        Map<String, Object> data;
        if (StringUtils.isBlank(context)) {
            data = JinjaMockContext.of(source);
        } else {
            try {
                data = Json.MAPPER.readValue(context, Map.class);
            } catch (Exception e) {
                throw new IllegalArgumentException("the context is not a json object: " + e.getMessage());
            }
        }
        byte[] rendered;
        try {
            rendered = templateRenderer.preview(previewType, previewV1, source, data);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            // a template error is what the preview is for: the message, not a 500
            throw new IllegalArgumentException("could not render the template: " + e.getMessage());
        }
        String extension = switch (previewType.output()) {
            case PDF -> ".pdf";
            case XML -> ".xml";
            case TEXT -> previewType.extension();
        };
        return RestUtil.transformToByteArrayResource("preview" + extension, OaTemplateRenderer.contentType(previewType),
                rendered);
    }

    @SneakyThrows
    private String previewSource(String id, MultipartFile template) {
        if (template != null && !template.isEmpty()) {
            return new String(template.getBytes(), StandardCharsets.UTF_8);
        }
        return templateService.findById(id).map(t -> new String(templateService.source(t), StandardCharsets.UTF_8))
                .orElseThrow(
                        () -> new IllegalArgumentException("a template source or the id of a template is required"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map.Entry<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.entry("error", e.getMessage()));
    }
}
