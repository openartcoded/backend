package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import tech.artcoded.websitev2.pages.task.ReminderTask;
import tech.artcoded.websitev2.pages.task.ReminderTaskDeleteOldAction;
import tech.artcoded.websitev2.pages.task.ReminderTaskRepository;
import tech.artcoded.websitev2.rest.util.CronUtil;

import java.io.IOException;
import java.util.Date;

@ChangeUnit(id = "delete-old-tasks-action", order = "35", author = "Nordine Bittich")
public class $35_AddReminderTaskDeleteOldAction {

  @RollbackExecution
  public void rollbackExecution() {
  }

  @Execution
  public void execute(ReminderTaskRepository taskRepository) throws IOException {
    if (taskRepository.findByActionKeyIsNotNull().stream()
        .noneMatch(t -> ReminderTaskDeleteOldAction.ACTION_KEY.equals(t.getActionKey()))) {
      var defaultMetadata = ReminderTaskDeleteOldAction.getDefaultMetadata();
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
