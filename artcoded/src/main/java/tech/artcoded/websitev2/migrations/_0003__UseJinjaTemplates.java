package tech.artcoded.websitev2.migrations;

import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Rollback;
import io.flamingock.api.annotations.TargetSystem;
import tech.artcoded.websitev2.mongodb.MigrationTargetSystem;
import tech.artcoded.websitev2.template.JinjaTemplatesInstaller;

/**
 * The jinja (v2) templates converted from the FreeMarker ones (resources templates/v2) become the default template of
 * every type: invoice, cv, timesheet, peppol invoice and credit note, email, action report, sms, calendar invite. The
 * previous (v1) templates are kept, and can be made the default again.
 * <p>
 * Waits for the file service (application.templates.file-service-timeout, 10 minutes by default): it stores and renders
 * the v2 templates. When it doesn't come up, the change fails and runs again at the next start.
 */
@TargetSystem(id = MigrationTargetSystem.ID)
@Change(id = "use-jinja-templates", author = "Nordine Bittich")
public class _0003__UseJinjaTemplates {

    @Apply
    public void apply(JinjaTemplatesInstaller installer) {
        installer.install();
    }

    @Rollback
    public void rollback(JinjaTemplatesInstaller installer) {
        installer.uninstall();
    }
}
