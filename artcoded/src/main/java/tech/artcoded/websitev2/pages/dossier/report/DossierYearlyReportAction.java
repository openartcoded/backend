package tech.artcoded.websitev2.pages.dossier.report;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.action.Action;
import tech.artcoded.websitev2.action.ActionMetadata;
import tech.artcoded.websitev2.action.ActionParameter;
import tech.artcoded.websitev2.action.ActionParameterType;
import tech.artcoded.websitev2.action.ActionResult;
import tech.artcoded.websitev2.action.StatusType;

/**
 * Mails the annual report (pdf attached) to the administrator: a year over all its dossiers, compared with the other
 * years, and the projection of the last three years. Scheduled the 20th of january for the year that just ended
 * (migration _0007).
 */
@Slf4j
@Component
public class DossierYearlyReportAction implements Action {
    public static final String ACTION_KEY = "DOSSIER_YEARLY_REPORT_ACTION";
    public static final String PARAMETER_YEAR = "year";
    /** 20th of january, 8:23 */
    public static final String DEFAULT_CRON = "0 23 8 20 1 *";

    private final DossierReportService reportService;

    public DossierYearlyReportAction(DossierReportService reportService) {
        this.reportService = reportService;
    }

    public static ActionMetadata defaultMetadata() {
        return ActionMetadata.builder().key(ACTION_KEY).title("Dossier Yearly Report Action")
                .description("Mails the annual report (pdf): the year over all its dossiers, compared with the other"
                        + " years, and where the last three years lead. By default the previous year.")
                .allowedParameters(List.of(ActionParameter.builder().key(PARAMETER_YEAR)
                        .parameterType(ActionParameterType.INTEGER).required(false)
                        .description("The year (e.g. 2025). Default: the previous year").build()))
                .defaultCronValue(DEFAULT_CRON).build();
    }

    @Override
    public ActionResult run(List<ActionParameter> parameters) {
        var resultBuilder = this.actionResultBuilder(parameters);
        List<String> messages = new ArrayList<>();
        try {
            var year = DossierReportAction.parameter(parameters, PARAMETER_YEAR).map(Integer::parseInt)
                    .orElseGet(() -> LocalDate.now(DossierReportService.ZONE).getYear() - 1);
            var file = reportService.generateYear(year);
            reportService.send(file, "Annual report %s".formatted(year));
            messages.add("report %s sent".formatted(file.fileName()));
            file.report().getInsights().forEach(i -> messages.add(i.getText()));
            return resultBuilder.finishedDate(new Date()).messages(messages).build();
        } catch (Exception e) {
            log.error("error while executing action", e);
            messages.add("error, see logs: %s".formatted(e.getMessage()));
            return resultBuilder.finishedDate(new Date()).messages(messages).status(StatusType.FAILURE).build();
        }
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
