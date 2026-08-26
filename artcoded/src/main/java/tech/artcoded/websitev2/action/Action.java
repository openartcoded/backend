package tech.artcoded.websitev2.action;

import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface Action {

    default Optional<ActionRequest> getDefaultActionRequest() {
        return Optional.empty();
    }
    
    default boolean shouldNotRun(List<ActionParameter> parameters) {
        return false;
    }

    default void afterRun() {
        // no op
    }

    ActionResult run(List<ActionParameter> parameters);

    ActionMetadata getMetadata();

    String getKey();

    default ActionResult.ActionResultBuilder actionResultBuilder(List<ActionParameter> parameters) {
        return ActionResult.builder().startedDate(new Date()).status(StatusType.SUCCESS).actionKey(this.getKey())
                .parameters(parameters);
    }

}
