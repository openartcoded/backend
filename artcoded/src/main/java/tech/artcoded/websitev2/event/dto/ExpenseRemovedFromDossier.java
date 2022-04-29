package tech.artcoded.websitev2.event.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.artcoded.websitev2.event.IEvent;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ExpenseRemovedFromDossier implements IEvent {
  private static final String EVENT_NAME = "EXPENSE_REMOVED_FROM_DOSSIER";

  private String dossierId;
  private String expenseRemovedId;

  @Override
  public String getEventName() {
    return EVENT_NAME;
  }
}


