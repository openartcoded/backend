package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.data.mongodb.core.MongoTemplate;
import tech.artcoded.websitev2.pages.cv.repository.CurriculumRepository;

import java.util.Date;

@ChangeUnit(id = "move-main-page-to-cv",
            order = "2",
            author = "Nordine Bittich")
public class $2_MoveMainPageToCvChangeLog {

  @Execution
  public void execution(CurriculumRepository curriculumRepository, MongoTemplate mongoTemplate) {
    if (mongoTemplate.collectionExists("mainPage")) {
      mongoTemplate.dropCollection("mainPage");
      curriculumRepository.findAll()
                          .stream()
                          .findFirst()
                          .ifPresent(curriculum -> curriculumRepository.save(curriculum.toBuilder()
                                                                                       .updatedDate(new Date())
                                                                                       .introduction("""
                                                                                                                I'm an experienced Senior Full-stack developer with strong Java and JavaScript knowledge.
                                                                                                                <br>I'm comfortable performing in fast-changing, result-driven environments within the web application domain.
                                                                                                                <br>I'm always eager to learn new technologies and adapt myself quickly into a new environment.
                                                                                                                <br>Knowledge sharing and provide internal coaching to the teams for building better products is exactly what
                                                                                                                I'm all about.
                                                                                                             """)
                                                                                       .build()
                          ));
    }
  }

  @RollbackExecution
  public void rollbackExecution(CurriculumRepository repository) {
  }
}
