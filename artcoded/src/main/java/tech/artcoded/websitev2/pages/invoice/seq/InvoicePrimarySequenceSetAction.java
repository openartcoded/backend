package tech.artcoded.websitev2.pages.invoice.seq;

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
public class InvoicePrimarySequenceSetAction implements Action {
  public static final String ACTION_KEY = "INVOICE_PRIMARY_SEQ_ACTION";
  public static final String ACTION_PARAMETER_NUMBER_TO_SET = "ACTION_PARAMETER_NUMBER_TO_SET";

  private final InvoicePrimarySequenceService primarySequenceService;

  public InvoicePrimarySequenceSetAction(InvoicePrimarySequenceService service) {
    this.primarySequenceService = service;
  }

  @Override
  public ActionResult run(List<ActionParameter> parameters) {
    var resultBuilder = this.actionResultBuilder(parameters);
    List<String> messages = new ArrayList<>();
    try {
      Long seq = parameters.stream()
          .filter(p -> ACTION_PARAMETER_NUMBER_TO_SET.equals(p.getKey()))
          .filter(p -> StringUtils.isNotEmpty(p.getValue()))
          .findFirst()
          .flatMap(p -> p.getParameterType().castLong(p.getValue())).orElse(1L);
      messages.add("seq to set " + seq);
      primarySequenceService.setValueTo(seq);

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
        .title("Invoice Seq set")
        .description("An action to reset the sequence every year.")
        .allowedParameters(List.of(ActionParameter.builder()
            .key(ACTION_PARAMETER_NUMBER_TO_SET)
            .description("Number to set. Default is 1")
            .parameterType(ActionParameterType.LONG)
            .required(false)
            .build()))
        .defaultCronValue("0 0 0 1 1 *")
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
