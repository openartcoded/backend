package tech.artcoded.event.v1.invoice;

import tech.artcoded.event.IEvent;

public non-sealed interface IInvoiceEvent extends IEvent {
  @Override
  default Version getVersion() {
    return Version.V1;
  }
}
