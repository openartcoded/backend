package tech.artcoded.websitev2.migrations;

import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Rollback;
import io.flamingock.api.annotations.TargetSystem;
import tech.artcoded.websitev2.mongodb.MigrationTargetSystem;
import tech.artcoded.websitev2.template.JinjaTemplatesInstaller;
import tech.artcoded.websitev2.template.OaTemplateType;

/**
 * New version of the dossier report template installed by _0004: a report of one month or of every month of a dossier
 * (title, month by month table, column labels) and a cleaner header (the logo alone: it carries the name of the
 * company). Installed as a new template, "Dossier report (bugfix)", made the default DOSSIER_REPORT template (waits for
 * the file service). The "Dossier report" template of _0004 is kept, no longer the default: it can be deleted from the
 * templates page.
 */
@TargetSystem(id = MigrationTargetSystem.ID)
@Change(id = "install-dossier-report-bugfix-template", author = "Nordine Bittich")
public class _0005__InstallDossierReportBugfixTemplate {

    @Apply
    public void apply(JinjaTemplatesInstaller installer) {
        installer.install(OaTemplateType.DOSSIER_REPORT);
    }

    @Rollback
    public void rollback() {
        // nothing to undo: when the installation fails (file service down), the current default is left untouched;
        // switching back to a v1 template here would replace the jinja report in production
    }
}
