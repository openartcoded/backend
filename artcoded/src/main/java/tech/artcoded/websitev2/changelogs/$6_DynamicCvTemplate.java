package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import tech.artcoded.websitev2.pages.cv.repository.CurriculumRepository;
import tech.artcoded.websitev2.pages.cv.service.CurriculumTemplateService;
import tech.artcoded.websitev2.rest.util.MockMultipartFile;

import java.io.IOException;

import static org.apache.commons.io.IOUtils.toByteArray;

@ChangeUnit(id = "dynamic-cv-template", order = "6", author = "Nordine Bittich")
public class $6_DynamicCvTemplate {

  @RollbackExecution
  public void rollbackExecution(CurriculumTemplateService templateService, CurriculumRepository repository) {
  }

  @Execution
  public void execute(CurriculumTemplateService templateService, CurriculumRepository repository) throws IOException {

    var oldTemplate = new ClassPathResource("cv/cv-template-2021.ftl");
    var cft = templateService.addTemplate("Template 2021", MockMultipartFile.builder()
        .bytes(toByteArray(oldTemplate.getInputStream()))
        .name(oldTemplate.getFilename())
        .contentType(MediaType.TEXT_HTML_VALUE)
        .originalFilename(oldTemplate.getFilename())
        .build());
    repository.findAll().stream().findFirst()
        .map(cv -> cv.toBuilder().freemarkerTemplateId(cft.getId()).build())
        .ifPresent(repository::save);

  }
}
