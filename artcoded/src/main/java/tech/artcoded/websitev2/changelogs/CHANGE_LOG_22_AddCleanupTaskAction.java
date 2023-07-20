package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import tech.artcoded.websitev2.action.CleanupActionResultAction;
import tech.artcoded.websitev2.pages.task.ReminderTask;
import tech.artcoded.websitev2.pages.task.ReminderTaskRepository;
import tech.artcoded.websitev2.rest.util.CronUtil;

import java.io.IOException;
import java.util.Date;

@ChangeUnit(id = "add-cleanup-task-action", order = "22", author = "Nordine Bittich")
public class CHANGE_LOG_22_AddCleanupTaskAction {

  @RollbackExecution
  public void rollbackExecution() {
  }

  @Execution
  public void execute(ReminderTaskRepository taskRepository) throws IOException {
    if (taskRepository.findByActionKeyIsNotNull().stream()
        .noneMatch(t -> CleanupActionResultAction.ACTION_KEY.equals(t.getActionKey()))) {
      var defaultMetadata = CleanupActionResultAction.getDefaultMetadata();
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
