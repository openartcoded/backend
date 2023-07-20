package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.data.mongodb.core.MongoTemplate;
import tech.artcoded.websitev2.pages.client.BillableClient;
import tech.artcoded.websitev2.pages.client.BillableClientRepository;
import tech.artcoded.websitev2.pages.timesheet.TimesheetRepository;
import tech.artcoded.websitev2.pages.timesheet.TimesheetSettings;

import java.io.IOException;
import java.math.BigDecimal;

import static java.util.Optional.ofNullable;

@ChangeUnit(id = "move-timesheet-settings", order = "19", author = "Nordine Bittich")
public class CHANGE_LOG_19_MoveTimeSheetSettings {

  @RollbackExecution
  public void rollbackExecution() {
  }

  @Execution
  public void execute(MongoTemplate mongoTemplate, TimesheetRepository repository,
      BillableClientRepository clientRepository) throws IOException {
    if (mongoTemplate.collectionExists("timesheetSettings")) {
      mongoTemplate.dropCollection("timesheetSettings");

      for (var ts : repository.findAll()) {
        if (ts.getSettings() == null) {
          var projectName = ofNullable(ts.getClientId()).flatMap(clientRepository::findById)
              .map(BillableClient::getProjectName).orElse("N/A");
          repository.save(ts.toBuilder()
              .settings(TimesheetSettings.builder()
                  .minHoursPerDay(BigDecimal.ZERO)
                  .maxHoursPerDay(new BigDecimal("8.5"))
                  .defaultProjectName(projectName)
                  .build())
              .build());
        }
      }
    }

  }

}
