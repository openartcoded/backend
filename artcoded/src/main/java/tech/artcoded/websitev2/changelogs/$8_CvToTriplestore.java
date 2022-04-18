package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.pages.cv.service.CurriculumRdfService;
import tech.artcoded.websitev2.pages.cv.service.CurriculumService;

import java.io.IOException;

@Slf4j
@ChangeUnit(id = "cv-triplestore",
  order = "8",
  author = "Nordine Bittich")
public class $8_CvToTriplestore {

  @RollbackExecution
  public void rollbackExecution(CurriculumRdfService rdfService) {
  }

  @Execution
  public void execute(CurriculumRdfService rdfService, CurriculumService curriculumService) throws IOException {
    rdfService.pushTriples(curriculumService.getFullCurriculum().getId());

  }
}
