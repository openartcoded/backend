package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.data.mongodb.core.MongoTemplate;
import tech.artcoded.websitev2.pages.personal.PersonalInfoRepository;

import java.io.IOException;

@ChangeUnit(id = "add-max-days-to-pay-to-personal-info", order = "17", author = "Nordine Bittich")
public class CHANGE_LOG_17_AddMaxDaysToPayToPersonalInfo {

  @RollbackExecution
  public void rollbackExecution() {
  }

  @Execution
  public void execute(MongoTemplate mongoTemplate,
      PersonalInfoRepository repository) throws IOException {
    if (mongoTemplate.collectionExists("currentBillTo")) {
      mongoTemplate.dropCollection("currentBillTo");
    }
    repository.findAll().forEach(personalInfo -> {
      repository.save(personalInfo.toBuilder()
          .maxDaysToPay(30)
          .build());
    });

  }

}
