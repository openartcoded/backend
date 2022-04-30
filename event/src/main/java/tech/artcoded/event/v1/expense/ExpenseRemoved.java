package tech.artcoded.event.v1.expense;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.artcoded.event.IEvent;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ExpenseRemoved implements IEvent {
  private String expenseId;
  private List<String> uploadIds;

  @Override
  public Version getVersion() {
    return Version.V1;
  }
}


