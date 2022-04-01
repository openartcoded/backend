package tech.artcoded.websitev2.pages.cv.service;

import freemarker.template.Configuration;
import freemarker.template.Template;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;
import tech.artcoded.websitev2.api.dto.ByteArrayContainer;
import tech.artcoded.websitev2.pages.cv.entity.Curriculum;
import tech.artcoded.websitev2.rest.util.PdfToolBox;

import java.io.StringReader;
import java.util.Map;
import java.util.stream.Collectors;


@Service
@Slf4j
public class CvToPrintService {
  private final CurriculumTemplateService curriculumTemplateService;

  public CvToPrintService(CurriculumTemplateService curriculumTemplateService) {
    this.curriculumTemplateService = curriculumTemplateService;
  }

  @SneakyThrows
  @Cacheable(cacheNames = "cvPdf",
             key = "'cvToPdfK'")
  public ByteArrayContainer cvToPdf(Curriculum curriculum) {
    String template = curriculumTemplateService.getFreemarkerTemplate(curriculum.getFreemarkerTemplateId());
    Map<String, Curriculum> data = Map.of("cv", curriculum.toBuilder()
                                                          .experiences(curriculum.getExperiences()
                                                                                 .stream()
                                                                                 .sorted()
                                                                                 .collect(Collectors.toList()))
                                                          .build());
    Template t = new Template("name", new StringReader(template),
                              new Configuration(Configuration.VERSION_2_3_31));
    String html = FreeMarkerTemplateUtils.processTemplateIntoString(t, data);
    return new ByteArrayContainer(PdfToolBox.generatePDFFromHTMLV2(html));
  }

  @CacheEvict(cacheNames = "cvPdf",
              allEntries = true)
  public void invalidateCache() {
    log.info("cv invalidated");
  }
}
