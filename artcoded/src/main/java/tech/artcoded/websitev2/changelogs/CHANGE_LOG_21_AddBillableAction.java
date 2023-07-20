package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import tech.artcoded.websitev2.pages.client.BillableClientAction;
import tech.artcoded.websitev2.pages.task.ReminderTask;
import tech.artcoded.websitev2.pages.task.ReminderTaskRepository;
import tech.artcoded.websitev2.rest.util.CronUtil;

import java.io.IOException;
import java.util.Date;

@ChangeUnit(id = "add-billable-action", order = "21", author = "Nordine Bittich")
public class CHANGE_LOG_21_AddBillableAction {

  @RollbackExecution
  public void rollbackExecution() {
  }

  @Execution
  public void execute(ReminderTaskRepository taskRepository) throws IOException {
    if (taskRepository.findByActionKeyIsNotNull().stream()
        .noneMatch(t -> BillableClientAction.ACTION_KEY.equals(t.getActionKey()))) {
      var defaultMetadata = BillableClientAction.getDefaultMetadata();
      taskRepository.save(ReminderTask.builder()
          .actionKey(defaultMetadata.getKey())
          .actionParameters(defaultMetadata.getAllowedParameters())
          .description(defaultMetadata.getDescription())
          .title(defaultMetadata.getTitle())
          .cronExpression(defaultMetadata.getDefaultCronValue())
          .inAppNotification(false)
          .persistResult(true)
          .nextDate(CronUtil.getNextDateFromCronExpression(defaultMetadata.getDefaultCronValue(), new Date()))
          .build());
    }

  }

}
