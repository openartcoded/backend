package tech.artcoded.websitev2.template;

import static org.springframework.ui.freemarker.FreeMarkerTemplateUtils.processTemplateIntoString;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.openapitools.client.api.TemplateRoutesApi;
import org.openapitools.client.model.RenderRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import freemarker.template.Configuration;
import freemarker.template.Template;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.utils.helper.Json;

/**
 * Renders a template with what its version needs: FreeMarker (+ chromium for a pdf) in the backend for a v1, the file
 * service api for a v2. A PDF type gives a pdf, XML the xml, TEXT the text (UTF-8).
 */
@Service
@Slf4j
public class OaTemplateRenderer {
    private final OaTemplateService templateService;
    private final TemplateRoutesApi templateRoutesApi;
    private final HtmlToPdfRenderer htmlToPdfRenderer;

    public OaTemplateRenderer(OaTemplateService templateService, TemplateRoutesApi templateRoutesApi,
            HtmlToPdfRenderer htmlToPdfRenderer) {
        this.templateService = templateService;
        this.templateRoutesApi = templateRoutesApi;
        this.htmlToPdfRenderer = htmlToPdfRenderer;
    }

    /**
     * @param data
     *            the root of the template model (e.g. invoice, personalInfo, logo)
     * @param fileName
     *            name of the rendered file, without extension (used by the file service)
     */
    public byte[] render(OaTemplate template, Map<String, Object> data, String fileName) {
        return switch (template.getVersion()) {
            case V1 -> renderV1(template.getType(), templateService.v1Source(template), data);
            case V2 -> renderV2(template, data, fileName);
        };
    }

    /**
     * Renders with the default template of the type. A type without default yet (fresh install, before the bundled
     * templates are added) renders its bundled template.
     */
    public byte[] renderDefault(OaTemplateType type, Map<String, Object> data, String fileName) {
        var template = templateService.getDefault(type);
        if (template.isPresent()) {
            return render(template.get(), data, fileName);
        }
        log.warn("no default {} template, using the bundled one {}", type, type.bundledTemplate());
        return renderBundled(type, data);
    }

    /**
     * The default template of a TEXT type (email, action, sms, calendar, cv rdf). These are the messages the
     * application uses to report problems, or pushes elsewhere: when the default template cannot be rendered (file
     * service down, broken template), the bundled one is used instead of losing the message.
     */
    public String renderDefaultText(OaTemplateType type, Map<String, Object> data) {
        if (type.output() != OaTemplateType.Output.TEXT) {
            throw new IllegalArgumentException("%s is not a text template type".formatted(type));
        }
        try {
            return new String(renderDefault(type, data, type.name().toLowerCase()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("could not render the default {} template, using the bundled one", type, e);
            return new String(renderBundled(type, data), StandardCharsets.UTF_8);
        }
    }

    byte[] renderBundled(OaTemplateType type, Map<String, Object> data) {
        return renderV1(type, readClasspath(type.bundledTemplate()), data);
    }

    @SneakyThrows
    byte[] renderV1(OaTemplateType type, String source, Map<String, Object> data) {
        Template template = new Template(type.name(), new StringReader(source),
                new Configuration(Configuration.VERSION_2_3_31));
        String output = processTemplateIntoString(template, data);
        log.debug(output);
        return switch (type.output()) {
            case PDF -> htmlToPdfRenderer.render(output);
            case XML, TEXT -> output.getBytes(StandardCharsets.UTF_8);
        };
    }

    @SneakyThrows
    byte[] renderV2(OaTemplate template, Map<String, Object> data, String fileName) {
        var context = template.getType().fileServiceContext().orElseThrow(() -> new IllegalStateException(
                "the file service cannot render %s templates".formatted(template.getType())));
        var request = new RenderRequest().templateId(template.getFileServiceTemplateId()).templateContext(context)
                .fileName(fileName).context(toPlainJson(data));
        var rendered = templateRoutesApi.render(request);
        try {
            return Files.readAllBytes(rendered.toPath());
        } finally {
            FileUtils.deleteQuietly(rendered);
        }
    }

    /**
     * The model as plain json values (maps, lists, strings, numbers), for the file service client, which serializes
     * with Gson: Gson would only read the fields (computed values like invoice.total come from getters) and write dates
     * in a locale format the templates can't parse. Jackson writes the getters and iso-8601 dates.
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> toPlainJson(Map<String, Object> data) {
        return Json.MAPPER.readValue(Json.MAPPER.writeValueAsString(data), Map.class);
    }

    @SneakyThrows
    static String readClasspath(String path) {
        try (var is = new ClassPathResource(path).getInputStream()) {
            return IOUtils.toString(is, StandardCharsets.UTF_8);
        }
    }
}
