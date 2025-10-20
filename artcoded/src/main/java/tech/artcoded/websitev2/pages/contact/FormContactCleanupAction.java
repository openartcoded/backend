package tech.artcoded.websitev2.pages.contact;

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
public class FormContactCleanupAction implements Action {
    public static final String PARAMETER_DAYS_BEFORE = "PARAMETER_DAYS_BEFORE";
    public static final String ACTION_KEY = "FORM_CONTACT_CLEANUP_ACTION";

    private final FormContactRepository repository;

    public FormContactCleanupAction(FormContactRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean shouldNotRun(List<ActionParameter> parameters) {
        if (getDateFromParameters(parameters) instanceof Tuple2<Long, Date>(Long _, Date date)) {
            return repository.countByCreationDateBefore(date) == 0;
        } else {
            return true;
        }
    }

    Tuple2<Long, Date> getDateFromParameters(List<ActionParameter> parameters) {
        Long daysBefore = parameters.stream().filter(p -> PARAMETER_DAYS_BEFORE.equals(p.getKey()))
                .filter(p -> StringUtils.isNotEmpty(p.getValue())).findFirst()
                .flatMap(p -> p.getParameterType().castLong(p.getValue())).orElse(365L);
        Date date = Date.from(ZonedDateTime.now().minusDays(daysBefore).toInstant());
        return new Tuple2<>(daysBefore, date);
    }

    @Override
    public ActionResult run(List<ActionParameter> parameters) {
        var resultBuilder = this.actionResultBuilder(parameters);

        List<String> messages = new ArrayList<>();
        messages.add("before cleaning, count %s".formatted(repository.count()));
        try {
            if (getDateFromParameters(parameters) instanceof Tuple2<Long, Date>(Long daysBefore, Date date)) {
                messages.add("days before: %s, date to search: %s".formatted(daysBefore, date.toString()));
                repository.deleteByCreationDateBefore(date);
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

    public static ActionMetadata getDefaultMetadata() {
        return ActionMetadata.builder().key(ACTION_KEY).title("Contact Cleanup Action")
                .description("An action to cleanup prospects/contacts every x days. Default is after 365 days")
                .defaultCronValue("0 45 1 * * *")
                .allowedParameters(List.of(ActionParameter.builder().key(PARAMETER_DAYS_BEFORE)
                        .parameterType(ActionParameterType.LONG).required(false)
                        .description("Represent a number of days before current date. Default is 365").build()))
                .build();
    }

    @Override
    public String getKey() {
        return ACTION_KEY;
    }

}
