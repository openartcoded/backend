package tech.artcoded.websitev2.pages.cv.service;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import tech.artcoded.websitev2.pages.cv.entity.Curriculum;
import tech.artcoded.websitev2.template.OaTemplateRenderer;
import tech.artcoded.websitev2.template.OaTemplateService;
import tech.artcoded.websitev2.template.OaTemplateType;
import tech.artcoded.websitev2.utils.dto.ByteArrayContainer;

import java.util.Map;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

@Service
@Slf4j
public class CvToPrintService {
    private final OaTemplateService templateService;
    private final OaTemplateRenderer templateRenderer;

    public CvToPrintService(OaTemplateService templateService, OaTemplateRenderer templateRenderer) {
        this.templateService = templateService;
        this.templateRenderer = templateRenderer;
    }

    /** Rendered with the template chosen on the cv, otherwise the default cv template. */
    @SneakyThrows
    @Cacheable(cacheNames = "cvPdf", key = "'cvToPdfK'")
    public ByteArrayContainer cvToPdf(Curriculum curriculum) {
        var template = templateService.findById(curriculum.getFreemarkerTemplateId())
                .filter(t -> t.getType() == OaTemplateType.CV)
                .or(() -> templateService.getDefault(OaTemplateType.CV))
                .orElseThrow(() -> new RuntimeException("no template found"));
        Map<String, Object> data = Map.of("cv",
                curriculum.toBuilder()
                        .experiences(curriculum.getExperiences().stream().sorted().collect(Collectors.toList()))
                        .personalProjects(curriculum.getPersonalProjects().stream()
                                .map(project -> project.toBuilder()
                                        .description(isNotEmpty(project.getDescription())
                                                ? project.getDescription().replaceAll("(\r\n|\n)", "<br>")
                                                : "")
                                        .build())
                                .toList())
                        .build());
        return new ByteArrayContainer(templateRenderer.render(template, data, "cv"));
    }

    @CacheEvict(cacheNames = "cvPdf", allEntries = true)
    public void invalidateCache() {
        log.info("cv invalidated");
    }
}
