package tech.artcoded.websitev2.migrations;

import java.util.Date;

import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Rollback;
import io.flamingock.api.annotations.TargetSystem;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.mongodb.MigrationTargetSystem;
import tech.artcoded.websitev2.pages.dossier.report.DossierReportAction;
import tech.artcoded.websitev2.pages.task.ReminderTask;
import tech.artcoded.websitev2.rest.util.CronUtil;
import tech.artcoded.websitev2.template.JinjaTemplatesInstaller;
import tech.artcoded.websitev2.template.OaTemplateType;

/**
 * The monthly dossier report: its jinja template becomes the default DOSSIER_REPORT template (waits for the file
 * service, like _0003), and a task runs the report action the 10th of every month (the open dossier, the previous
 * month). The task can be changed or disabled in the tasks page.
 */
@Slf4j
@TargetSystem(id = MigrationTargetSystem.ID)
@Change(id = "add-dossier-report", author = "Nordine Bittich")
public class _0004__AddDossierReport {

    @Apply
    public void apply(JinjaTemplatesInstaller installer, MongoTemplate mongoTemplate) {
        installer.install(OaTemplateType.DOSSIER_REPORT);
        if (mongoTemplate.exists(reportTasks(), ReminderTask.class)) {
            log.info("a task already runs {}", DossierReportAction.ACTION_KEY);
            return;
        }
        var metadata = DossierReportAction.defaultMetadata();
        mongoTemplate.save(ReminderTask.builder().actionKey(metadata.getKey())
                .actionParameters(metadata.getAllowedParameters()).title(metadata.getTitle())
                .description(metadata.getDescription()).cronExpression(metadata.getDefaultCronValue())
                // the action mails the report itself: no second mail with the action result
                .sendMail(false).inAppNotification(true).persistResult(true)
                .nextDate(CronUtil.getNextDateFromCronExpression(metadata.getDefaultCronValue(), new Date())).build());
        log.info("task {} added ({})", metadata.getKey(), metadata.getDefaultCronValue());
    }

    @Rollback
    public void rollback(JinjaTemplatesInstaller installer, MongoTemplate mongoTemplate) {
        mongoTemplate.remove(reportTasks(), ReminderTask.class);
        installer.uninstall(OaTemplateType.DOSSIER_REPORT);
    }

    /** the tasks running the report action (MongoTemplate rather than the repository: available in every context) */
    static Query reportTasks() {
        return Query.query(Criteria.where("actionKey").is(DossierReportAction.ACTION_KEY));
    }
}
