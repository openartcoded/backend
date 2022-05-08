package tech.artcoded.event.v1.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.artcoded.event.IEvent;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class AdministrativeDocumentAddedOrUpdated implements IEvent {
  private String documentId;
  private String title;
  private String description;
  private String uploadId;

  @Override
  public Version getVersion() {
    return Version.V1;
  }
}


