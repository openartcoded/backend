package tech.artcoded.event.v1.dossier;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.artcoded.event.IEvent;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ExpensesAddedToDossier implements IEvent {
  private String dossierId;
  private Set<String> expenseIds;

  @Override
  public Version getVersion() {
    return Version.V1;
  }
}


