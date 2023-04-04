package tech.artcoded.event.v1.document;

import tech.artcoded.event.IEvent;

public non-sealed interface IDocumentEvent extends IEvent {
  @Override
  default Version getVersion() {
    return Version.V1;
  }
}
