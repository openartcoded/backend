package tech.artcoded.websitev2.migrations;

import java.util.Date;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Rollback;
import io.flamingock.api.annotations.TargetSystem;
import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.mongodb.MigrationTargetSystem;
import tech.artcoded.websitev2.pages.dossier.report.DossierQuarterlyReportAction;
import tech.artcoded.websitev2.pages.dossier.report.DossierReportAction;
import tech.artcoded.websitev2.pages.task.ReminderTask;
import tech.artcoded.websitev2.rest.util.CronUtil;

/**
 * A task runs the quarterly dossier report action (no parameter: the last 3 months, the dossier closed last) the 10th
 * of january, april, july and october. It can be changed or disabled in the tasks page.
 */
@Slf4j
@TargetSystem(id = MigrationTargetSystem.ID)
@Change(id = "add-quarterly-dossier-report-task", author = "Nordine Bittich")
public class _0006__AddQuarterlyDossierReportTask {

    @Apply
    public void apply(MongoTemplate mongoTemplate) {
        if (mongoTemplate.exists(quarterlyReportTasks(), ReminderTask.class)) {
            log.info("a task already runs {}", DossierQuarterlyReportAction.ACTION_KEY);
            return;
        }
        var metadata = DossierQuarterlyReportAction.defaultMetadata();
        var cron = DossierReportAction.DEFAULT_CRON_QUARTER;
        mongoTemplate.save(ReminderTask.builder().actionKey(metadata.getKey())
                .actionParameters(metadata.getAllowedParameters()).title(metadata.getTitle())
                .description(metadata.getDescription()).cronExpression(metadata.getDefaultCronValue())
                // the action mails the report itself: no second mail with the action result
                .sendMail(false).inAppNotification(true).persistResult(true)
                .nextDate(CronUtil.getNextDateFromCronExpression(cron, new Date())).build());
        log.info("task {} added ({})", metadata.getKey(), cron);
    }

    @Rollback
    public void rollback(MongoTemplate mongoTemplate) {
        mongoTemplate.remove(quarterlyReportTasks(), ReminderTask.class);
    }

    static Query quarterlyReportTasks() {
        return Query.query(Criteria.where("actionKey").is(DossierQuarterlyReportAction.ACTION_KEY));
    }
}
