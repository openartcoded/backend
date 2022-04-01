package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.action.ActionMetadata;
import tech.artcoded.websitev2.action.ActionService;
import tech.artcoded.websitev2.mongodb.MongoDumpAction;
import tech.artcoded.websitev2.pages.blog.TagCleanupAction;
import tech.artcoded.websitev2.pages.memzagram.MemZaGramAction;
import tech.artcoded.websitev2.pages.task.ReminderTask;
import tech.artcoded.websitev2.pages.task.ReminderTaskService;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
@ChangeUnit(id = "default-tasks",
            order = "10",
            author = "Nordine Bittich")
public class $10_DefaultTasks {

  @RollbackExecution
  public void rollbackExecution() {
  }


  @Execution
  public void execute(ActionService actionService, ReminderTaskService taskService) throws IOException {
    if (taskService.findAll().isEmpty()) {
      List<ActionMetadata> allowedActions = actionService.getAllowedActions();
      record SaveAction(String actionKey, String cronExpression) {
      }
      Consumer<SaveAction> saveTask = sa -> allowedActions.stream()
                                                          .filter(actionMetadata -> sa.actionKey()
                                                                                      .equals(actionMetadata.getKey()))
                                                          .findFirst().ifPresent(am -> {
                taskService.save(ReminderTask.builder()
                                             .actionKey(am.getKey())
                                             .actionParameters(List.of())
                                             .cronExpression(sa.cronExpression())
                                             .title(am.getTitle())
                                             .description(am.getDescription())
                                             .build(), false);
              });

      saveTask.accept(new SaveAction(MemZaGramAction.ACTION_KEY, "*/40 * * * * *"));
      saveTask.accept(new SaveAction(TagCleanupAction.ACTION_KEY, "0 */5 * * * *"));
      saveTask.accept(new SaveAction(MongoDumpAction.ACTION_KEY, "0 30 1 2,9,16,23 * ?"));

    }

  }
}
