package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.pages.personal.PersonalInfoRepository;
import tech.artcoded.websitev2.pages.personal.PersonalInfoService;

@Slf4j
@ChangeUnit(id = "peppol-related-changes", order = "44", author = "Nordine Bittich")
public class CHANGE_LOG_44_PEPPOL {

  @RollbackExecution
  public void rollbackExecution() {
  }

  @Execution
  public void execute(PersonalInfoService personalInfoService,
      PersonalInfoRepository repository)
      throws IOException {
    var personalInfo = personalInfoService.get();
    repository.save(
        personalInfo.toBuilder().organizationCountryCode("BE").build());

  }
}
