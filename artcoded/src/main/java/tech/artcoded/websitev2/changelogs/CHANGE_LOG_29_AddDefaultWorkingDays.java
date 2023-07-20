package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import tech.artcoded.websitev2.pages.client.BillableClientRepository;

import java.io.IOException;
import java.time.DayOfWeek;
import java.util.List;

@ChangeUnit(id = "add-default-working-days", order = "29", author = "Nordine Bittich")
public class CHANGE_LOG_29_AddDefaultWorkingDays {

  @RollbackExecution
  public void rollbackExecution() {
  }

  @Execution
  public void execute(BillableClientRepository repository) throws IOException {

    repository.findAll().stream()
        .filter(client -> client.getDefaultWorkingDays() == null || client.getDefaultWorkingDays().isEmpty())
        .forEach(client -> {
          repository.save(client.toBuilder().defaultWorkingDays(List.of(
              DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)).build());
        });
    ;

  }

}
