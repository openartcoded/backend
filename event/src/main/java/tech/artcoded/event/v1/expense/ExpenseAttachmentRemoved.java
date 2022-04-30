package tech.artcoded.event.v1.expense;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.artcoded.event.IEvent;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ExpenseAttachmentRemoved implements IEvent {
  private String expenseId;
  private String uploadId;

  @Override
  public Version getVersion() {
    return Version.V1;
  }
}


