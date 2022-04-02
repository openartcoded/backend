package tech.artcoded.websitev2.pages.immo;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import tech.artcoded.websitev2.action.Action;
import tech.artcoded.websitev2.action.ActionMetadata;
import tech.artcoded.websitev2.action.ActionParameter;
import tech.artcoded.websitev2.action.ActionParameterType;
import tech.artcoded.websitev2.action.ActionResult;
import tech.artcoded.websitev2.action.StatusType;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
@Deprecated(forRemoval = true)
public class ImmoFilterAction implements Action {
  public static final String PARAMETER_MONTH_BEFORE = "PARAMETER_MONTH_BEFORE";
  public static final String ACTION_KEY = "IMMO_FILTER_ACTION";

  private final ImmoFilterRepository repository;

  public ImmoFilterAction(ImmoFilterRepository repository) {
    this.repository = repository;
  }


  @Override
  public ActionResult run(List<ActionParameter> parameters) {
    var resultBuilder = this.actionResultBuilder(parameters);
    List<String> messages = new ArrayList<>();
    messages.add("before cleaning, count %s".formatted(repository.count()));
    try {
      Long monthsBefore = parameters.stream().filter(p -> PARAMETER_MONTH_BEFORE.equals(p.getKey()))
                                    .filter(p -> StringUtils.isNotEmpty(p.getValue())).findFirst()
                                    .flatMap(p -> p.getParameterType().castLong(p.getValue())).orElse(6L);
      Date date = Date.from(ZonedDateTime.now().minusMonths(monthsBefore).toInstant());
      messages.add("months before: %s, date to search: %s".formatted(monthsBefore, date.toString()));
      repository.deleteByDateCreationBefore(date);
      messages.add("after cleaning, count %s".formatted(repository.count()));
      return resultBuilder.finishedDate(new Date()).messages(messages).build();
    }
    catch (Exception e) {
      log.error("error while executing action", e);
      messages.add("error, see logs: %s".formatted(e.getMessage()));
      return resultBuilder.finishedDate(new Date()).messages(messages).status(StatusType.FAILURE).build();
    }

  }

  @Override
  public ActionMetadata getMetadata() {
    return ActionMetadata.builder()
                         .key(ACTION_KEY)
                         .title("Immo Web Filter Action")
                         .description("An action to perform a cleanup of the immoweb collections")
                         .allowedParameters(List.of(ActionParameter.builder()
                                                                   .key(PARAMETER_MONTH_BEFORE)
                                                                   .parameterType(ActionParameterType.LONG)
                                                                   .required(false)
                                                                   .description("Represent a number of months before current date. Default is 6")
                                                                   .build()))
                         .defaultCronValue("0 0 0 * * *")
                         .build();
  }

  @Override
  public String getKey() {
    return ACTION_KEY;
  }

}
