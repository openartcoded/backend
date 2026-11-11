package tech.artcoded.websitev2.migrations;

import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Rollback;
import io.flamingock.api.annotations.TargetSystem;
import tech.artcoded.websitev2.mongodb.MigrationTargetSystem;
import tech.artcoded.websitev2.template.JinjaTemplatesInstaller;
import tech.artcoded.websitev2.template.OaTemplateType;

/**
 * The business plan: its jinja template becomes the default BUSINESS_PLAN template (waits for the file service, like
 * _0004). Until then, the bundled FreeMarker template is used. It can be replaced in the templates page.
 */
@TargetSystem(id = MigrationTargetSystem.ID)
@Change(id = "add-business-plan-template", author = "Nordine Bittich")
public class _0009__AddBusinessPlanTemplate {

    @Apply
    public void apply(JinjaTemplatesInstaller installer) {
        installer.install(OaTemplateType.BUSINESS_PLAN);
    }

    @Rollback
    public void rollback(JinjaTemplatesInstaller installer) {
        installer.uninstall(OaTemplateType.BUSINESS_PLAN);
    }
}
