package tech.artcoded.websitev2.pages.dossier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;

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
public class AddExpenseToCloseDossierAction implements Action {

    public static final String ACTION_KEY = "ADD_EXPENSE_TO_CLOSED_DOSSIER_ACTION";
    public static final String PARAMETER_DOSSIER_ID = "PARAMETER_DOSSIER_ID";
    public static final String PARAMETER_FEE_IDS = "PARAMETER_FEE_IDS";

    private final CloseActiveDossierService closeActiveDossierService;

    @Inject
    public AddExpenseToCloseDossierAction(CloseActiveDossierService closeActiveDossierService) {
        this.closeActiveDossierService = closeActiveDossierService;
    }

    public static ActionMetadata getDefaultMetadata() {
        return ActionMetadata.builder().key(ACTION_KEY).title("Add expense to closed dossier Action")
                .description("An action to fix a dossier with missing expenses")
                .allowedParameters(List.of(
                        ActionParameter.builder().key(PARAMETER_DOSSIER_ID).parameterType(ActionParameterType.STRING)
                                .required(true).description("The dossier id").build(),
                        ActionParameter.builder().key(PARAMETER_FEE_IDS).parameterType(ActionParameterType.STRING)
                                .required(false).description("The expense ids to add (separated by a ,)").build()))
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
            var dossierId = parameters.stream().filter(p -> PARAMETER_DOSSIER_ID.equals(p.getKey()))
                    .map(ActionParameter::getValue).filter(p -> StringUtils.isNotEmpty(p)).findFirst()
                    .orElseThrow(() -> new RuntimeException("dossier id is missing"));
            messages.add("dossierId: " + dossierId);

            var expenseIds = parameters.stream().filter(p -> PARAMETER_FEE_IDS.equals(p.getKey()))
                    .map(ActionParameter::getValue).filter(p -> StringUtils.isNotEmpty(p)).findFirst()
                    .map(p -> Arrays.asList(p.split(",")))
                    .orElseThrow(() -> new RuntimeException("expense ids missing"));
            messages.add("expenseIds: " + expenseIds);

            closeActiveDossierService.addExpenseToClosedDossier(dossierId, expenseIds);
            return resultBuilder.finishedDate(new Date()).messages(messages).build();

        } catch (Exception e) {
            log.error("error while executing action", e);
            messages.add("error, see logs: %s".formatted(e.getMessage()));
            return resultBuilder.messages(messages).finishedDate(new Date()).status(StatusType.FAILURE).build();
        }
    }

}
