package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.pages.task.ReminderTask;
import tech.artcoded.websitev2.pages.task.ReminderTaskRepository;
import tech.artcoded.websitev2.rest.util.CronUtil;
import tech.artcoded.websitev2.upload.BackupFilesAction;

import java.io.IOException;
import java.util.Date;

@Slf4j
@ChangeUnit(id = "add-backup-files-action",
  order = "23",
  author = "Nordine Bittich")
public class $23_AddBackupFilesAction {

  @RollbackExecution
  public void rollbackExecution() {
  }

  @Execution
  public void execute(ReminderTaskRepository taskRepository) throws IOException {
    if (
      taskRepository.findByActionKeyIsNotNull().stream()
        .noneMatch(t -> BackupFilesAction.ACTION_KEY.equals(t.getActionKey()))
    ) {
      var defaultMetadata = BackupFilesAction.getDefaultMetadata();
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
