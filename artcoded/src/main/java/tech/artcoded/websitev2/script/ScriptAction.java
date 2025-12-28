
package tech.artcoded.websitev2.script;

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
public class ScriptAction implements Action {

    public static final String ACTION_KEY = "SCRIPT_ACTION";
    public static final String PARAMETER_SCRIPT = "PARAMETER_SCRIPT";

    private final ScriptService scriptService;

    public ScriptAction(ScriptService scriptService) {
        this.scriptService = scriptService;
    }

    public static ActionMetadata getDefaultMetadata() {
        return ActionMetadata.builder().key(ACTION_KEY).title("Script Action").description("An action to run a script")
                .allowedParameters(List.of(
                        ActionParameter.builder().key(PARAMETER_SCRIPT).parameterType(ActionParameterType.BIG_STRING)
                                .required(true).description("The script to run").build()))
                .defaultCronValue("0 30 0 * * *").build();

    }

    @Override
    public ActionMetadata getMetadata() {
        return getDefaultMetadata();
    }

    @Override
    public String getKey() {
        return ACTION_KEY;
    }

    @Override
    public ActionResult run(List<ActionParameter> parameters) {
        Date date = new Date();
        var resultBuilder = this.actionResultBuilder(parameters).startedDate(date);

        List<String> messages = new ArrayList<>();
        try {
            messages.add("starting action");
            var script = parameters.stream().filter(p -> PARAMETER_SCRIPT.equals(p.getKey()))
                    .map(ActionParameter::getValue).filter(p -> StringUtils.isNotEmpty(p)).findFirst()
                    .orElseThrow(() -> new RuntimeException("script is missing"));
            messages.add("script: " + script);

            var result = scriptService.experimentalRunManually(script);

            messages.add("out:\n" + result);
            return resultBuilder.finishedDate(new Date()).messages(messages).build();

        } catch (Exception e) {
            log.error("error while executing action", e);
            messages.add("error, see logs: %s".formatted(e.getMessage()));
            return resultBuilder.messages(messages).finishedDate(new Date()).status(StatusType.FAILURE).build();
        }
    }

}
