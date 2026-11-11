package tech.artcoded.websitev2.pages.dossier.report;

import java.util.Set;

import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.AfterDeleteEvent;
import org.springframework.data.mongodb.core.mapping.event.AfterSaveEvent;
import org.springframework.stereotype.Component;

import tech.artcoded.websitev2.pages.dossier.Dossier;
import tech.artcoded.websitev2.pages.fee.Fee;
import tech.artcoded.websitev2.pages.invoice.InvoiceGeneration;
import tech.artcoded.websitev2.pages.personal.PersonalInfo;
import tech.artcoded.websitev2.template.OaTemplate;
import tech.artcoded.websitev2.template.OaTemplateType;

/**
 * Invalidates the cached reports when what they are computed from is saved or deleted: a dossier (closed, reopened to
 * set the VAT, an invoice or an expense added or removed...), an invoice, an expense, the organization (name, logo) or
 * a dossier report template. Every report: they all compare with the other periods and count what is not processed yet.
 */
@Component
public class ReportCacheInvalidator extends AbstractMongoEventListener<Object> {
    static final Set<Class<?>> SOURCES = Set.of(Dossier.class, InvoiceGeneration.class, Fee.class, PersonalInfo.class);

    private final ReportCache reportCache;

    public ReportCacheInvalidator(ReportCache reportCache) {
        this.reportCache = reportCache;
    }

    static boolean concernsReports(Object document) {
        if (document instanceof OaTemplate template) {
            // the business plan pdf is cached with the reports
            return template.getType() == OaTemplateType.DOSSIER_REPORT
                    || template.getType() == OaTemplateType.BUSINESS_PLAN;
        }
        return document != null && SOURCES.contains(document.getClass());
    }

    @Override
    public void onAfterSave(AfterSaveEvent<Object> event) {
        if (concernsReports(event.getSource())) {
            reportCache.invalidateAll("%s %s saved".formatted(event.getSource().getClass().getSimpleName(),
                    event.getDocument() == null ? "" : event.getDocument().get("_id")));
        }
    }

    @Override
    public void onAfterDelete(AfterDeleteEvent<Object> event) {
        var type = event.getType();
        // a deleted template may be a report template: invalidated too
        if (type != null && (SOURCES.contains(type) || OaTemplate.class.equals(type))) {
            reportCache.invalidateAll("%s deleted".formatted(type.getSimpleName()));
        }
    }
}
