package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import tech.artcoded.websitev2.action.ActionParameter;
import tech.artcoded.websitev2.action.ActionParameterType;
import tech.artcoded.websitev2.pages.task.ReminderTaskRepository;

import java.io.IOException;
import java.util.stream.Stream;

import static tech.artcoded.websitev2.pages.timesheet.TimesheetAction.ACTION_KEY;
import static tech.artcoded.websitev2.pages.timesheet.TimesheetAction.PARAMETER_CLIENT_ID;

@ChangeUnit(id = "add-action-parameter-client-id", order = "20", author = "Nordine Bittich")
public class CHANGE_LOG_20_AddActionParameterClientId {

  @RollbackExecution
  public void rollbackExecution() {
  }

  @Execution
  public void execute(ReminderTaskRepository taskRepository) throws IOException {
    taskRepository.findByActionKeyIsNotNull().stream()
        .filter(t -> ACTION_KEY.equals(t.getActionKey())
            && t.getActionParameters().stream().noneMatch(p -> PARAMETER_CLIENT_ID.equals(p.getKey())))
        .forEach(t -> taskRepository.save(t.toBuilder().actionParameters(
            Stream.concat(t.getActionParameters().stream(),
                Stream.of(ActionParameter.builder().key(PARAMETER_CLIENT_ID)
                    .parameterType(ActionParameterType.OPTION)
                    .required(true)
                    .description("Client")
                    .value(null).build()))
                .toList())
            .build()));

  }

}
