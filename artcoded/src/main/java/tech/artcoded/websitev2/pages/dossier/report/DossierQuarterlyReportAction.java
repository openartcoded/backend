package tech.artcoded.websitev2.pages.dossier.report;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.action.Action;
import tech.artcoded.websitev2.action.ActionMetadata;
import tech.artcoded.websitev2.action.ActionParameter;
import tech.artcoded.websitev2.action.ActionResult;
import tech.artcoded.websitev2.action.StatusType;

/**
 * Mails the report of the last 3 complete months (pdf attached) to the administrator, for the dossier closed last (the
 * open one when none is closed yet). No parameter. Scheduled the 10th of january, april, july and october (migration
 * _0006): the report of the quarter that just ended, its dossier closed at the beginning of the month.
 */
@Slf4j
@Component
public class DossierQuarterlyReportAction implements Action {
    public static final String ACTION_KEY = "DOSSIER_QUARTERLY_REPORT_ACTION";
    /** months covered, the previous month included */
    static final int MONTHS = 3;
    /** 10th of january, april, july and october, 8:17 */
    public static final String DEFAULT_CRON = "0 17 8 10 1,4,7,10 *";

    private final DossierReportService reportService;

    public DossierQuarterlyReportAction(DossierReportService reportService) {
        this.reportService = reportService;
    }

    public static ActionMetadata defaultMetadata() {
        return ActionMetadata.builder().key(ACTION_KEY).title("Dossier Quarterly Report Action")
                .description("Mails the report (pdf) of the last 3 complete months for the dossier closed last,"
                        + " compared with the same months of the previous years.")
                .allowedParameters(List.of()).defaultCronValue(DEFAULT_CRON).build();
    }

    /** the last {@link #MONTHS} complete months before today */
    static DossierReportCalculator.Period period(LocalDate today) {
        var to = YearMonth.from(today).minusMonths(1);
        return new DossierReportCalculator.Period(to.minusMonths(MONTHS - 1), to);
    }

    @Override
    public ActionResult run(List<ActionParameter> parameters) {
        var resultBuilder = this.actionResultBuilder(parameters);
        List<String> messages = new ArrayList<>();
        try {
            var dossier = reportService.lastClosedDossier().or(reportService::activeDossier);
            if (dossier.isEmpty()) {
                messages.add("there is no dossier: no report");
                return resultBuilder.finishedDate(new Date()).messages(messages).build();
            }
            var period = period(LocalDate.now(DossierReportService.ZONE));
            var file = reportService.generate(dossier.get(), period.from(), period.to());
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

    @Override
    public ActionMetadata getMetadata() {
        return defaultMetadata();
    }

    @Override
    public String getKey() {
        return ACTION_KEY;
    }
}
