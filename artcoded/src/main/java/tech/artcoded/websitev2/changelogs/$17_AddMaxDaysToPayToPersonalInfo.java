package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import tech.artcoded.websitev2.pages.personal.PersonalInfo;
import tech.artcoded.websitev2.pages.personal.PersonalInfoRepository;
import tech.artcoded.websitev2.pages.personal.PersonalInfoService;

import java.io.IOException;


@Slf4j
@ChangeUnit(id = "add-max-days-to-pay-to-personal-info",
  order = "17",
  author = "Nordine Bittich")
public class $17_AddMaxDaysToPayToPersonalInfo {

  @RollbackExecution
  public void rollbackExecution() {
  }

  @Execution
  public void execute(PersonalInfoService personalInfoService,
                      MongoTemplate mongoTemplate,
                      PersonalInfoRepository repository) throws IOException {
    if (mongoTemplate.collectionExists("currentBillTo")) {
      mongoTemplate.dropCollection("currentBillTo");
    }
    PersonalInfo personalInfo = personalInfoService.get();
    repository.save(personalInfo.toBuilder()
      .maxDaysToPay(30)
      .build());

  }

}
