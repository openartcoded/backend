package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.pages.client.BillableClientAction;
import tech.artcoded.websitev2.pages.task.ReminderTask;
import tech.artcoded.websitev2.pages.task.ReminderTaskRepository;

import java.io.IOException;


@Slf4j
@ChangeUnit(id = "add-billable-action",
  order = "21",
  author = "Nordine Bittich")
public class $21_AddBillableAction {

  @RollbackExecution
  public void rollbackExecution() {
  }

  @Execution
  public void execute(ReminderTaskRepository taskRepository) throws IOException {
    if (
      taskRepository.findByActionKeyIsNotNull().stream()
        .noneMatch(t -> BillableClientAction.ACTION_KEY.equals(t.getActionKey()))
    ) {
      var defaultMetadata = BillableClientAction.getDefaultMetadata();
      taskRepository.save(ReminderTask.builder()
        .actionKey(defaultMetadata.getKey())
        .actionParameters(defaultMetadata.getAllowedParameters())
        .description(defaultMetadata.getDescription())
        .title(defaultMetadata.getTitle())
        .cronExpression(defaultMetadata.getDefaultCronValue())
        .inAppNotification(false)
        .persistResult(true)
        .build());
    }

  }

}
