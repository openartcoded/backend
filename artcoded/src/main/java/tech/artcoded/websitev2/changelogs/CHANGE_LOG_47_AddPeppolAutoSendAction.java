
package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.io.IOException;
import java.util.Date;
import tech.artcoded.websitev2.pages.task.ReminderTask;
import tech.artcoded.websitev2.pages.task.ReminderTaskRepository;
import tech.artcoded.websitev2.peppol.PeppolAutoSendAction;
import tech.artcoded.websitev2.rest.util.CronUtil;

@ChangeUnit(id = "add-peppol-auto-send-action", order = "47", author = "Nordine Bittich")
public class CHANGE_LOG_47_AddPeppolAutoSendAction {

    @RollbackExecution
    public void rollbackExecution() {
    }

    @Execution
    public void execute(ReminderTaskRepository taskRepository) throws IOException {
        if (taskRepository.findByActionKeyIsNotNull().stream()
                .noneMatch(t -> PeppolAutoSendAction.ACTION_KEY.equals(t.getActionKey()))) {
            var defaultMetadata = PeppolAutoSendAction.defaultMetadata();
            taskRepository.save(ReminderTask.builder().actionKey(defaultMetadata.getKey())
                    .actionParameters(defaultMetadata.getAllowedParameters())
                    .description(defaultMetadata.getDescription()).title(defaultMetadata.getTitle())
                    .cronExpression(defaultMetadata.getDefaultCronValue()).inAppNotification(false).persistResult(true)
                    .sendMail(true)
                    .nextDate(CronUtil.getNextDateFromCronExpression(defaultMetadata.getDefaultCronValue(), new Date()))
                    .build());
        }
    }
}
