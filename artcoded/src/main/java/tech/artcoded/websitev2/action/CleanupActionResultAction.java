package tech.artcoded.websitev2.action;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.utils.dto.Tuple.Tuple2;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CleanupActionResultAction implements Action {
    public static final String ACTION_KEY = "CLEANUP_ACTION_RESULT_ACTION";
    public static final String ACTION_PARAMETER_NUMBER_OF_DAYS = "ACTION_PARAMETER_NUMBER_OF_DAYS";
    private final ActionResultRepository repository;

    public CleanupActionResultAction(ActionResultRepository repository) {
        this.repository = repository;
    }

    public static ActionMetadata getDefaultMetadata() {
        return ActionMetadata.builder().key(ACTION_KEY).title("Cleanup action results")
                .description("An action to periodically cleanup action results from previous tasks")
                .allowedParameters(List.of(ActionParameter.builder().key(ACTION_PARAMETER_NUMBER_OF_DAYS)
                        .description("Number of days before cleaning it. Default is 6")
                        .parameterType(ActionParameterType.LONG).required(false).build()))
                .defaultCronValue("0 0 0 1 * *").build();
    }

    Tuple2<Long, Date> getFromParam(List<ActionParameter> parameters) {
        Long daysBefore = parameters.stream().filter(p -> ACTION_PARAMETER_NUMBER_OF_DAYS.equals(p.getKey()))
                .filter(p -> StringUtils.isNotEmpty(p.getValue())).findFirst()
                .flatMap(p -> p.getParameterType().castLong(p.getValue())).orElse(6L);
        Date searchDate = Date.from(ZonedDateTime.now().minusDays(daysBefore).toInstant());
        return new Tuple2<>(daysBefore, searchDate);
    }

    @Override
    public boolean shouldNotRun(List<ActionParameter> parameters) {
        if (getFromParam(parameters) instanceof Tuple2<Long, Date>(Long _, Date searchDate)) {
            return repository.countByFinishedDateBefore(searchDate) == 0;
        } else {
            return true; // should not run
        }

    }

    @Override
    public ActionResult run(List<ActionParameter> parameters) {
        var resultBuilder = this.actionResultBuilder(parameters);
        List<String> messages = new ArrayList<>();
        try {

            if (getFromParam(parameters) instanceof Tuple2<Long, Date>(Long daysBefore, Date searchDate)) {
                messages.add("days before: %s, date to search: %s".formatted(daysBefore, searchDate.toString()));
                repository.deleteByFinishedDateBefore(searchDate);
                messages.add("after cleaning, count %s".formatted(repository.count()));
            }
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
