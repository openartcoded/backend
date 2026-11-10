package tech.artcoded.websitev2.pages.dossier.report;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.mapping.event.AfterDeleteEvent;
import org.springframework.data.mongodb.core.mapping.event.AfterSaveEvent;

import tech.artcoded.websitev2.pages.dossier.Dossier;
import tech.artcoded.websitev2.pages.fee.Fee;
import tech.artcoded.websitev2.pages.task.ReminderTask;
import tech.artcoded.websitev2.template.OaTemplate;
import tech.artcoded.websitev2.template.OaTemplateType;

class ReportCacheInvalidatorTest {
    private final ReportCache cache = mock(ReportCache.class);
    private final ReportCacheInvalidator invalidator = new ReportCacheInvalidator(cache);

    private static AfterSaveEvent<Object> saved(Object source) {
        return new AfterSaveEvent<>(source, new Document("_id", "x"), "collection");
    }

    /** a delete event of a type, as the listener of every type receives it */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static AfterDeleteEvent<Object> deleted(Class<?> type) {
        return new AfterDeleteEvent(new Document("_id", "x"), type, "collection");
    }

    @Test
    void aDossierReopenedToSetItsVatInvalidatesTheReports() {
        invalidator.onAfterSave(saved(Dossier.builder().id("q3").closed(false).build()));
        verify(cache).invalidateAll(anyString());
    }

    @Test
    void anExpenseSavedInvalidatesTheReports() {
        invalidator.onAfterSave(saved(Fee.builder().id("f").build()));
        verify(cache).invalidateAll(anyString());
    }

    @Test
    void aNewReportTemplateInvalidatesTheReports() {
        invalidator.onAfterSave(saved(OaTemplate.builder().type(OaTemplateType.DOSSIER_REPORT).build()));
        verify(cache).invalidateAll(anyString());
    }

    @Test
    void otherDocumentsDont() {
        invalidator.onAfterSave(saved(OaTemplate.builder().type(OaTemplateType.INVOICE).build()));
        invalidator.onAfterSave(saved(ReminderTask.builder().build()));
        verify(cache, never()).invalidateAll(anyString());
    }

    @Test
    void aDeletedInvoiceOrDossierInvalidatesTheReports() {
        invalidator.onAfterDelete(deleted(Dossier.class));
        verify(cache).invalidateAll(anyString());
        invalidator.onAfterDelete(deleted(ReminderTask.class));
        verify(cache).invalidateAll(anyString());
    }
}
