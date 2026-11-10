package tech.artcoded.websitev2.pages.dossier.report;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.action.Action;
import tech.artcoded.websitev2.action.ActionMetadata;
import tech.artcoded.websitev2.action.ActionParameter;
import tech.artcoded.websitev2.action.ActionParameterType;
import tech.artcoded.websitev2.action.ActionResult;
import tech.artcoded.websitev2.action.StatusType;

/**
 * Mails the monthly report of a dossier (pdf attached, one month) to the administrator. Scheduled the 5th of every
 * month for the open dossier and the previous month (migration _0004), it can also be run from the actions page for
 * another dossier or month.
 */
@Slf4j
@Component
public class DossierReportAction implements Action {
    public static final String ACTION_KEY = "DOSSIER_REPORT_ACTION";
    public static final String PARAMETER_DOSSIER_ID = "dossierId";
    public static final String PARAMETER_MONTH = "month";
    /** 5 th of every month, 7:47 */
    public static final String DEFAULT_CRON = "0 47 7 10 * *";

    private final DossierReportService reportService;

    public DossierReportAction(DossierReportService reportService) {
        this.reportService = reportService;
    }

    public static ActionMetadata defaultMetadata() {
        return ActionMetadata.builder().key(ACTION_KEY).title("Dossier Report Action")
                .description(
                        "Mails the report of a dossier (pdf): the month and the year so far compared with the previous"
                                + " years, expenses, clients, vat and what is left to process. By default the open"
                                + " dossier and the previous month.")
                .allowedParameters(List.of(
                        ActionParameter.builder().key(PARAMETER_DOSSIER_ID).parameterType(ActionParameterType.STRING)
                                .required(false).description("Id of the dossier. Default: the open dossier").build(),
                        ActionParameter.builder().key(PARAMETER_MONTH).parameterType(ActionParameterType.STRING)
                                .required(false)
                                .description("Month of the report, yyyy-MM (e.g. 2026-10). Default: the previous"
                                        + " month for the open dossier, the last month invoiced for a closed one")
                                .build()))
                .defaultCronValue(DEFAULT_CRON).build();
    }

    @Override
    public ActionResult run(List<ActionParameter> parameters) {
        var resultBuilder = this.actionResultBuilder(parameters);
        List<String> messages = new ArrayList<>();
        try {
            var dossierId = parameter(parameters, PARAMETER_DOSSIER_ID);
            var month = parameter(parameters, PARAMETER_MONTH).map(YearMonth::parse);
            // always one month (every month of a dossier: the dossier page; the last 3 months: the quarterly action)
            var dossier = dossierId.isPresent()
                    ? Optional.of(reportService.findDossier(dossierId.get()).orElseThrow(
                            () -> new IllegalArgumentException("dossier %s not found".formatted(dossierId.get()))))
                    : reportService.activeDossier();
            var generated = dossier
                    .map(d -> reportService.generate(d, month.orElseGet(() -> reportService.defaultMonth(d))));
            if (generated.isEmpty()) {
                messages.add("there is no open dossier: no report");
                return resultBuilder.finishedDate(new Date()).messages(messages).build();
            }
            var file = generated.get();
            reportService.send(file);
            messages.add("report %s sent".formatted(file.fileName()));
            file.report().getInsights().forEach(i -> messages.add(i.getText()));
            return resultBuilder.finishedDate(new Date()).messages(messages).build();
        } catch (Exception e) {
            log.error("error while executing action", e);
            messages.add("error, see logs: %s".formatted(e.getMessage()));
            return resultBuilder.finishedDate(new Date()).messages(messages).status(StatusType.FAILURE).build();
        }
    }

    static Optional<String> parameter(List<ActionParameter> parameters, String key) {
        return Optional.ofNullable(parameters).orElseGet(List::of).stream().filter(p -> key.equals(p.getKey()))
                .map(ActionParameter::getValue).filter(StringUtils::isNotBlank).map(String::strip).findFirst();
    }

    @Override
    public ActionMetadata getMetadata() {
        return defaultMetadata();
    }

    @Override
    public String getKey() {
        return ACTION_KEY;
    }
}
