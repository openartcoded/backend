package tech.artcoded.websitev2.pages.settings.menu;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import tech.artcoded.websitev2.action.Action;
import tech.artcoded.websitev2.action.ActionMetadata;
import tech.artcoded.websitev2.action.ActionParameter;
import tech.artcoded.websitev2.action.ActionParameterType;
import tech.artcoded.websitev2.action.ActionResult;
import tech.artcoded.websitev2.action.StatusType;

@Component
@Slf4j
public class ResetMenuLinkAction implements Action {
  public static final String ACTION_KEY = "RESET_MENU_ACTION";
  public static final String ACTION_PARAMETER_MENU_LINK = "ACTION_PARAMETER_MENU_LINK";

  private final MenuLinkRepository repository;

  public ResetMenuLinkAction(MenuLinkRepository repository) {
    this.repository = repository;
  }

  @Override
  public ActionResult run(List<ActionParameter> parameters) {
    var resultBuilder = this.actionResultBuilder(parameters);
    List<String> messages = new ArrayList<>();

    messages.add("starting reset menu link...");
    try {

      parameters.stream()
          .filter(p -> ACTION_PARAMETER_MENU_LINK.equals(p.getKey()))
          .filter(p -> StringUtils.isNotEmpty(p.getValue()))
          .findFirst()
          .flatMap(p -> repository.findById(p.getValue()))
          .ifPresent(m -> repository.save(
              m.toBuilder().numberOfTimesClicked(0L).build()));
      messages.add("delete done");
      return resultBuilder.finishedDate(new Date())
          .status(StatusType.SUCCESS)
          .messages(messages)
          .build();
    } catch (Exception e) {
      log.error("error while executing action", e);

      messages.add("error, see logs: %s".formatted(e.getMessage()));
      return resultBuilder.messages(messages)
          .finishedDate(new Date())
          .status(StatusType.FAILURE)
          .build();
    }
  }

  @Override
  public ActionMetadata getMetadata() {
    var options = this.repository.findAll()
        .stream()
        .map(m -> Map.entry(m.getId(), m.getTitle()))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    return ActionMetadata.builder()
        .key(ACTION_KEY)
        .title("Reset menu link Action")
        .description("An action to perform a reset of menu link clicks.")
        .allowedParameters(
            List.of(ActionParameter.builder()
                .parameterType(ActionParameterType.OPTION)
                .options(options)
                .key(ACTION_PARAMETER_MENU_LINK)
                .description("The menu link to update")
                .build()))
        .defaultCronValue("0 30 1 2,15 * ?")
        .build();
  }

  @Override
  public String getKey() {
    return ACTION_KEY;
  }
}
