package tech.artcoded.websitev2.template;

import java.nio.charset.StandardCharsets;

import org.apache.commons.io.FilenameUtils;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.rest.util.MockMultipartFile;

/**
 * At startup, a type without any template gets its bundled template, as a v1 and the default of the type. Every type
 * then shows up in the templates page with a default that can be replaced. Not a migration: it uploads files.
 */
@Component
@Slf4j
public class BundledTemplatesInitializer {
    private final OaTemplateRepository repository;
    private final OaTemplateService templateService;

    public BundledTemplatesInitializer(OaTemplateRepository repository, OaTemplateService templateService) {
        this.repository = repository;
        this.templateService = templateService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void addMissingBundledTemplates() {
        for (var type : OaTemplateType.values()) {
            if (repository.existsByType(type)) {
                continue;
            }
            try {
                var path = type.bundledTemplate();
                var fileName = FilenameUtils.getName(path);
                var file = MockMultipartFile.builder().name(fileName).originalFilename(fileName)
                        .contentType(type.isXml() ? MediaType.APPLICATION_XML_VALUE : MediaType.TEXT_PLAIN_VALUE)
                        .bytes(OaTemplateRenderer.readClasspath(path).getBytes(StandardCharsets.UTF_8)).build();
                var saved = templateService.save(null, FilenameUtils.getBaseName(path), "bundled template", type, true,
                        true, file);
                log.info("bundled {} template added as default ({})", type, saved.getId());
            } catch (Exception e) {
                log.error("could not add the bundled {} template, the classpath one is used", type, e);
            }
        }
    }
}
