package tech.artcoded.websitev2.action;

import java.util.Date;
import java.util.List;

public interface Action {
  default boolean noOp() {
    return false;
  }
  ActionResult run(List<ActionParameter> parameters);

  ActionMetadata getMetadata();

  String getKey();

  default ActionResult.ActionResultBuilder actionResultBuilder(List<ActionParameter> parameters) {
    return ActionResult.builder().startedDate(new Date())
      .status(StatusType.SUCCESS)
      .actionKey(this.getKey())
      .parameters(parameters);
  }

}

