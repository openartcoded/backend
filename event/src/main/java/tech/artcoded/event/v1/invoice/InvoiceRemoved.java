package tech.artcoded.event.v1.invoice;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.artcoded.event.IEvent;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class InvoiceRemoved implements IEvent {
  private String invoiceId;
  private String uploadId;
  private boolean logicalDelete;

  @Override
  public Version getVersion() {
    return Version.V1;
  }
}
