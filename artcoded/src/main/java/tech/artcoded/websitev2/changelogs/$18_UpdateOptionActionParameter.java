package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import tech.artcoded.websitev2.pages.personal.PersonalInfoRepository;

import java.io.IOException;


@Slf4j
@ChangeUnit(id = "update-option-action-parameter",
  order = "18",
  author = "Nordine Bittich")
public class $18_UpdateOptionActionParameter {

  @RollbackExecution
  public void rollbackExecution() {
  }

  @Execution
  public void execute(MongoTemplate mongoTemplate,
                      PersonalInfoRepository repository) throws IOException {
    if (mongoTemplate.collectionExists("reminderTask")) {
      Query all = new Query();
      mongoTemplate.updateMulti(all, Update.update("actionParameters.$[].options", "{}"), "reminderTask");
    }
    repository.findAll().forEach(personalInfo -> {
      repository.save(personalInfo.toBuilder()
        .maxDaysToPay(30)
        .build());
    });


  }

}
