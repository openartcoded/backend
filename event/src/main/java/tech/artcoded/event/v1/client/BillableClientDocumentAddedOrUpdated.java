package tech.artcoded.event.v1.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.artcoded.event.IEvent;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class BillableClientDocumentAddedOrUpdated implements IEvent {
  private String clientId;
  private String uploadId;
  private String contractStatus;

  @Override
  public Version getVersion() {
    return Version.V1;
  }
}


