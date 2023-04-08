package tech.artcoded.event.v1.expense;

import tech.artcoded.event.IEvent;

public non-sealed interface IExpenseEvent extends IEvent {
  @Override
  default Version getVersion() {
    return Version.V1;
  }
}
