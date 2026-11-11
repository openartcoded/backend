package tech.artcoded.websitev2.migrations;

import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Rollback;
import io.flamingock.api.annotations.TargetSystem;
import tech.artcoded.websitev2.mongodb.MigrationTargetSystem;
import tech.artcoded.websitev2.template.JinjaTemplatesInstaller;
import tech.artcoded.websitev2.template.OaTemplateType;

/**
 * The sparql update pushing the cv to the triplestore becomes a template (CV_RDF): its jinja version becomes the
 * default (waits for the file service, like _0004). Until then, the bundled FreeMarker one (cv-rdf.ftl) is used. It can
 * be replaced in the templates page.
 */
@TargetSystem(id = MigrationTargetSystem.ID)
@Change(id = "add-cv-rdf-template", author = "Nordine Bittich")
public class _0010__AddCvRdfTemplate {

    @Apply
    public void apply(JinjaTemplatesInstaller installer) {
        installer.install(OaTemplateType.CV_RDF);
    }

    @Rollback
    public void rollback(JinjaTemplatesInstaller installer) {
        installer.uninstall(OaTemplateType.CV_RDF);
    }
}
