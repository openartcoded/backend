package tech.artcoded.event.v1.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class BillableClientDocumentAddedOrUpdated implements IClientEvent {
  private String clientId;
  private String uploadId;
  private String contractStatus;

}
