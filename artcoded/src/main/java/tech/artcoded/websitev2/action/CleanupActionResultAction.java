package tech.artcoded.websitev2.action;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
public class CleanupActionResultAction implements Action {
  public static final String ACTION_KEY = "CLEANUP_ACTION_RESULT_ACTION";
  public static final String ACTION_PARAMETER_NUMBER_OF_DAYS = "ACTION_PARAMETER_NUMBER_OF_DAYS";
  private final ActionService actionService;

  public CleanupActionResultAction(ActionService actionService) {
    this.actionService = actionService;
  }

  public static ActionMetadata getDefaultMetadata() {
    return ActionMetadata.builder()
      .key(ACTION_KEY)
      .title("Cleanup action results")
      .description("An action to periodically cleanup action results from previous tasks")
      .allowedParameters(List.of(ActionParameter.builder()
        .key(ACTION_PARAMETER_NUMBER_OF_DAYS)
        .description("Number of days before cleaning it. Default is 30")
        .parameterType(ActionParameterType.LONG)
        .required(false)
        .build()))
      .defaultCronValue("0 0 0 1 * *")
      .build();

  }

  @Override
  public ActionResult run(List<ActionParameter> parameters) {
    var resultBuilder = this.actionResultBuilder(parameters);
    Date date = new Date();
    List<String> messages = new ArrayList<>();
    try {
      Long daysBefore = parameters.stream()
        .filter(p -> ACTION_PARAMETER_NUMBER_OF_DAYS.equals(p.getKey()))
        .filter(p -> StringUtils.isNotEmpty(p.getValue()))
        .findFirst()
        .flatMap(p -> p.getParameterType().castLong(p.getValue())).orElse(6L);
      Date searchDate = Date.from(ZonedDateTime.now().minusDays(daysBefore).toInstant());
      messages.add("days before: %s, date to search: %s".formatted(daysBefore, searchDate.toString()));
      actionService.deleteByFinishedDateBefore(date);
      messages.add("after cleaning, count %s".formatted(actionService.count()));
      return resultBuilder.finishedDate(new Date()).messages(messages).build();
    } catch (Exception e) {
      log.error("error while executing action", e);
      messages.add("error, see logs: %s".formatted(e.getMessage()));
      return resultBuilder.finishedDate(new Date()).messages(messages).status(StatusType.FAILURE).build();

    }
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
