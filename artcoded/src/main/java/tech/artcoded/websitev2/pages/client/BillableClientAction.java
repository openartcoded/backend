package tech.artcoded.websitev2.pages.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.artcoded.websitev2.action.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
public class BillableClientAction implements Action {
    public static final String ACTION_KEY = "BILLABLE_CLIENT_ACTION";
    private final BillableClientService service;

    public BillableClientAction(BillableClientService service) {
        this.service = service;
    }

    @Override
    public ActionResult run(List<ActionParameter> parameters) {
        Date date = new Date();
        var resultBuilder = this.actionResultBuilder(parameters).startedDate(date);

        List<String> messages = new ArrayList<>();

        try {
            List<BillableClient> canStart = service.findByContractStatusInAndEndDateIsBefore(
                    List.of(ContractStatus.NOT_STARTED_YET, ContractStatus.DONE), date);

            service.updateAll(canStart.stream().map(s -> s.toBuilder().contractStatus(ContractStatus.ONGOING).build())
                    .peek(c -> messages.add("contract status for '%s' set to started".formatted(c.getName())))
                    .toList());
            List<BillableClient> canEnd = service.findByContractStatusInAndEndDateIsBefore(
                    List.of(ContractStatus.NOT_STARTED_YET, ContractStatus.ONGOING), date);

            service.updateAll(canEnd.stream().map(s -> s.toBuilder().contractStatus(ContractStatus.DONE).build())
                    .peek(c -> messages.add("contract status for '%s' set to ended".formatted(c.getName()))).toList());

            return resultBuilder.finishedDate(new Date()).messages(messages).build();

        } catch (Exception e) {
            log.error("error while executing action", e);
            messages.add("error, see logs: %s".formatted(e.getMessage()));
            return resultBuilder.messages(messages).finishedDate(new Date()).status(StatusType.FAILURE).build();
        }

    }

    @Override
    public ActionMetadata getMetadata() {
        return getDefaultMetadata();
    }

    public static ActionMetadata getDefaultMetadata() {
        return ActionMetadata.builder().key(ACTION_KEY).title("Billable Client contract status update")
                .description(
                        "An action to check periodically either if the contract status should be set to ongoing/ended")
                .allowedParameters(List.of()).defaultCronValue("*/40 * * * * *").build();
    }

    @Override
    public String getKey() {
        return ACTION_KEY;
    }
}
