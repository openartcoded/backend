package tech.artcoded.websitev2.pages.fee;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import tech.artcoded.event.v1.expense.ExpenseNotPaid;
import tech.artcoded.websitev2.action.Action;
import tech.artcoded.websitev2.action.ActionMetadata;
import tech.artcoded.websitev2.action.ActionParameter;
import tech.artcoded.websitev2.action.ActionResult;
import tech.artcoded.websitev2.action.StatusType;
import tech.artcoded.websitev2.event.ExposedEventService;

@Slf4j
@Component
public class FeeReminderPayAction implements Action {

    public static final String ACTION_KEY = "EXPENSE_REMINDER_PAY_ACTION";
    private final FeeRepository repository;
    private final ExposedEventService exposedEventService;

    public FeeReminderPayAction(FeeRepository repository, ExposedEventService exposedEventService) {
        this.repository = repository;
        this.exposedEventService = exposedEventService;
    }

    public static ActionMetadata getDefaultMetadata() {
        return ActionMetadata.builder().key(ACTION_KEY).title("Expense Reminder Pay Action")
                .description("An action to check if there are expenses that must be processed")
                .allowedParameters(List.of()).defaultCronValue("0 0 3 * * *").build();

    }

    @Override
    public ActionResult run(List<ActionParameter> parameters) {
        var resultBuilder = this.actionResultBuilder(parameters);
        List<String> messages = new ArrayList<>();
        List<String> expenseIds = new ArrayList<>();
        try {
            var expensesNotPaid = repository.findByArchived(false);
            if (!expensesNotPaid.isEmpty()) {
                messages.add("there are %s expense(s) not paid:".formatted(expensesNotPaid.size()));
                for (var expense : expensesNotPaid) {
                    messages.add(expense.getSubject());
                    expenseIds.add(expense.getId());
                }
            }
            exposedEventService.sendEvent(ExpenseNotPaid.builder().expenseIds(expenseIds).build());
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
