package tech.artcoded.websitev2.notification;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import tech.artcoded.websitev2.action.*;
import tech.artcoded.websitev2.utils.dto.Tuple.Tuple2;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
public class NotificationCleanupAction implements Action {
    public static final String PARAMETER_DAYS_BEFORE = "PARAMETER_DAYS_BEFORE";
    public static final String ACTION_KEY = "NOTIFICATION_CLEANUP_ACTION";

    private final NotificationRepository repository;

    public NotificationCleanupAction(NotificationRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean shouldNotRun(List<ActionParameter> parameters) {
        if (getFromParam(parameters) instanceof Tuple2<Long, Date>(Long _, Date searchDate)) {
            return repository.countBySeenIsTrueAndReceivedDateBefore(searchDate) == 0;
        } else {
            return true;
        }

    }

    Tuple2<Long, Date> getFromParam(List<ActionParameter> parameters) {
        Long daysBefore = parameters.stream().filter(p -> PARAMETER_DAYS_BEFORE.equals(p.getKey()))
                .filter(p -> StringUtils.isNotEmpty(p.getValue())).findFirst()
                .flatMap(p -> p.getParameterType().castLong(p.getValue())).orElse(6L);
        Date date = Date.from(ZonedDateTime.now().minusDays(daysBefore).toInstant());
        return new Tuple2<>(daysBefore, date);
    }

    @Override
    public ActionResult run(List<ActionParameter> parameters) {
        var resultBuilder = this.actionResultBuilder(parameters);

        List<String> messages = new ArrayList<>();
        messages.add("before cleaning, count %s".formatted(repository.count()));
        try {
            if (getFromParam(parameters) instanceof Tuple2<Long, Date>(Long daysBefore, Date date)) {
                messages.add("days before: %s, date to search: %s".formatted(daysBefore, date.toString()));
                repository.deleteBySeenIsTrueAndReceivedDateBefore(date);
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
        return ActionMetadata.builder().key(ACTION_KEY).title("Notification Cleanup Action")
                .description("An action to cleanup notifications every x days. Default is after 2 days")
                .defaultCronValue("0 0 0 * * *")
                .allowedParameters(List.of(ActionParameter.builder().key(PARAMETER_DAYS_BEFORE)
                        .parameterType(ActionParameterType.LONG).required(false)
                        .description("Represent a number of days before current date. Default is 2").build()))
                .build();
    }

    @Override
    public String getKey() {
        return ACTION_KEY;
    }

}
