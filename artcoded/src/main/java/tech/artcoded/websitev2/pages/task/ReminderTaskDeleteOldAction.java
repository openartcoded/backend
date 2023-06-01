package tech.artcoded.websitev2.pages.task;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.action.Action;
import tech.artcoded.websitev2.action.ActionMetadata;
import tech.artcoded.websitev2.action.ActionParameter;
import tech.artcoded.websitev2.action.ActionParameterType;
import tech.artcoded.websitev2.action.ActionResult;
import tech.artcoded.websitev2.action.StatusType;

@Component
@Slf4j
public class ReminderTaskDeleteOldAction implements Action {
  public static final String ACTION_KEY = "REMINDER_TASK_DELETE_OLD_ACTION";
  public static final String ACTION_PARAMETER_NUMBER_OF_DAYS = "ACTION_PARAMETER_NUMBER_OF_DAYS";

  private final ReminderTaskService reminderTaskService;

  public ReminderTaskDeleteOldAction(ReminderTaskService reminderTaskService) {
    this.reminderTaskService = reminderTaskService;
  }

  @Override
  public ActionResult run(List<ActionParameter> parameters) {
    var resultBuilder = this.actionResultBuilder(parameters);
    List<String> messages = new ArrayList<>();
    try {
      Long daysBefore = parameters.stream()
          .filter(p -> ACTION_PARAMETER_NUMBER_OF_DAYS.equals(p.getKey()))
          .filter(p -> StringUtils.isNotEmpty(p.getValue()))
          .findFirst()
          .flatMap(p -> p.getParameterType().castLong(p.getValue())).orElse(10L);
      Date searchDate = Date.from(ZonedDateTime.now().minusDays(daysBefore).toInstant());
      messages.add("days before: %s, date to search: %s".formatted(daysBefore, searchDate.toString()));
      var tasks = reminderTaskService.findByDisabledTrueAndActionKeyIsNullAndUpdatedDateBefore(searchDate);
      for (var task : tasks) {
        messages.add("delete task %s".formatted(task.getTitle()));
        reminderTaskService.deleteWithoutNotify(task.getId());
      }
      return resultBuilder.finishedDate(new Date()).messages(messages).build();
    } catch (Exception e) {
      log.error("error while executing action", e);
      messages.add("error, see logs: %s".formatted(e.getMessage()));
      return resultBuilder.finishedDate(new Date()).messages(messages).status(StatusType.FAILURE).build();

    }
  }

  public static ActionMetadata getDefaultMetadata() {
    return ActionMetadata.builder()
        .key(ACTION_KEY)
        .title("Cleanup old tasks")
        .description("An action to periodically cleanup old tasks.")
        .allowedParameters(List.of(ActionParameter.builder()
            .key(ACTION_PARAMETER_NUMBER_OF_DAYS)
            .description("Number of days before cleaning it. Default is 10")
            .parameterType(ActionParameterType.LONG)
            .required(false)
            .build()))
        .defaultCronValue("0 0 5 * * *")
        .build();

  }

  @Override
  public ActionMetadata getMetadata() {
    return getDefaultMetadata();
  }

  @Override
  public String getKey() {
    return ACTION_KEY;
  }
}
