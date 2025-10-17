package tech.artcoded.event;

import tech.artcoded.event.v1.client.IClientEvent;
import tech.artcoded.event.v1.document.IDocumentEvent;
import tech.artcoded.event.v1.dossier.IDossierEvent;
import tech.artcoded.event.v1.expense.IExpenseEvent;
import tech.artcoded.event.v1.invoice.IInvoiceEvent;
import tech.artcoded.event.v1.timesheet.ITimesheetEvent;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(value = { "version", "timestamp", "eventName" }, allowGetters = true)
public sealed interface IEvent
        permits IClientEvent, IDocumentEvent, IExpenseEvent, IDossierEvent, IInvoiceEvent, ITimesheetEvent {
    enum Version {
        V1
    }

    default long getTimestamp() {
        return System.currentTimeMillis();
    }

    Version getVersion();

    default String getEventName() {
        return this.getClass().getSimpleName();
    }
}
