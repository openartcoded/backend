package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.pages.cv.repository.CurriculumRepository;
import tech.artcoded.websitev2.pages.cv.service.CurriculumService;
import tech.artcoded.websitev2.utils.helper.IdGenerators;

@Slf4j
@ChangeUnit(id = "add-uuid-experience", order = "44", author = "Nordine Bittich")
public class CHANGE_LOG_44_AddUuidExperience {

  @RollbackExecution
  public void rollbackExecution() {
  }

  @Execution
  public void execute(CurriculumRepository curriculumRepository, CurriculumService cvService)
      throws IOException {
    var cv = curriculumRepository.findAll().getFirst();
    if (cv == null) {
      log.info("no cv found. continue...");
      return;
    }
    log.info("cv: {}", new ObjectMapper().writeValueAsString(cv));
    cv.getExperiences().forEach(exp -> {
      if (exp.getUuid() == null)
        exp.setUuid(IdGenerators.get());
    });

    log.info("cv after: {}", new ObjectMapper().writeValueAsString(cv));
    cvService.update(cv);
  }
}
