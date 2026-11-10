package tech.artcoded.websitev2.template;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

import tech.artcoded.websitev2.rest.util.RestUtil;

/**
 * Single place to manage every template (admin only, like every route not opened in the security config). Per type:
 * any number of v1 and v2 templates, one default (the one rendered).
 */
@RestController
@RequestMapping("/api/template")
public class OaTemplateController {
    private final OaTemplateService templateService;

    public OaTemplateController(OaTemplateService templateService) {
        this.templateService = templateService;
    }

    /** a type, what it renders, and the extension a v2 template file of this type gets */
    public record TemplateTypeInfo(OaTemplateType type, OaTemplateType.Output output, String extension) {
    }

    @GetMapping("/types")
    public List<TemplateTypeInfo> types() {
        return Arrays.stream(OaTemplateType.values())
                .map(t -> new TemplateTypeInfo(t, t.output(), t.extension())).toList();
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
            @RequestParam("type") OaTemplateType type,
            @RequestParam(value = "v1", defaultValue = "false") boolean v1,
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

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map.Entry<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.entry("error", e.getMessage()));
    }
}
