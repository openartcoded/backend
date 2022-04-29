package tech.artcoded.websitev2.event.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.artcoded.websitev2.event.IEvent;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ExpensesAddedToDossier implements IEvent {
  private static final String EVENT_NAME = "EXPENSES_ADDED_TO_DOSSIER";

  private String dossierId;
  private Set<String> addedExpenseIds;

  @Override
  public String getEventName() {
    return EVENT_NAME;
  }
}


