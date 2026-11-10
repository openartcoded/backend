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
import tech.artcoded.websitev2.pages.dossier.report.DossierYearlyReportAction;
import tech.artcoded.websitev2.pages.task.ReminderTask;
import tech.artcoded.websitev2.rest.util.CronUtil;
import tech.artcoded.websitev2.template.JinjaTemplatesInstaller;
import tech.artcoded.websitev2.template.OaTemplateType;

/**
 * The annual report: the dossier report template that also renders a year (its dossiers, the other years, the
 * projection) is installed as a new template, "Dossier report (annual)", made the default DOSSIER_REPORT template
 * (waits for the file service; the previous ones are kept). A task runs the yearly report action the 20th of january
 * (the previous year); it can be changed or disabled in the tasks page.
 */
@Slf4j
@TargetSystem(id = MigrationTargetSystem.ID)
@Change(id = "add-yearly-dossier-report", author = "Nordine Bittich")
public class _0007__AddYearlyDossierReport {

    @Apply
    public void apply(JinjaTemplatesInstaller installer, MongoTemplate mongoTemplate) {
        installer.install(OaTemplateType.DOSSIER_REPORT);
        if (mongoTemplate.exists(yearlyReportTasks(), ReminderTask.class)) {
            log.info("a task already runs {}", DossierYearlyReportAction.ACTION_KEY);
            return;
        }
        var metadata = DossierYearlyReportAction.defaultMetadata();
        mongoTemplate.save(ReminderTask.builder().actionKey(metadata.getKey())
                .actionParameters(metadata.getAllowedParameters()).title(metadata.getTitle())
                .description(metadata.getDescription()).cronExpression(metadata.getDefaultCronValue())
                // the action mails the report itself: no second mail with the action result
                .sendMail(false).inAppNotification(true).persistResult(true)
                .nextDate(CronUtil.getNextDateFromCronExpression(metadata.getDefaultCronValue(), new Date())).build());
        log.info("task {} added ({})", metadata.getKey(), metadata.getDefaultCronValue());
    }

    @Rollback
    public void rollback(MongoTemplate mongoTemplate) {
        // the template: nothing to undo (a failed installation leaves the current default untouched)
        mongoTemplate.remove(yearlyReportTasks(), ReminderTask.class);
    }

    static Query yearlyReportTasks() {
        return Query.query(Criteria.where("actionKey").is(DossierYearlyReportAction.ACTION_KEY));
    }
}
