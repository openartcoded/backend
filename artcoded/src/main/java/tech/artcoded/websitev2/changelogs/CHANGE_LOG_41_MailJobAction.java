package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.io.IOException;
import java.util.Date;
import tech.artcoded.websitev2.pages.mail.MailSendAction;
import tech.artcoded.websitev2.pages.task.ReminderTask;
import tech.artcoded.websitev2.pages.task.ReminderTaskRepository;
import tech.artcoded.websitev2.rest.util.CronUtil;

@ChangeUnit(id = "mail-job-action", order = "41", author = "Nordine Bittich")
public class CHANGE_LOG_41_MailJobAction {

    @RollbackExecution
    public void rollbackExecution() {
    }

    @Execution
    public void execute(ReminderTaskRepository taskRepository) throws IOException {
        if (taskRepository.findByActionKeyIsNotNull().stream()
                .noneMatch(t -> MailSendAction.ACTION_KEY.equals(t.getActionKey()))) {
            var defaultMetadata = MailSendAction.defaultMetadata();
            taskRepository.save(ReminderTask.builder().actionKey(defaultMetadata.getKey())
                    .actionParameters(defaultMetadata.getAllowedParameters())
                    .description(defaultMetadata.getDescription()).title(defaultMetadata.getTitle())
                    .cronExpression(defaultMetadata.getDefaultCronValue()).inAppNotification(false).persistResult(true)
                    .nextDate(CronUtil.getNextDateFromCronExpression(defaultMetadata.getDefaultCronValue(), new Date()))
                    .build());
        }
    }
}
