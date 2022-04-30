package tech.artcoded.websitev2.event.dto.dossier;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.artcoded.websitev2.event.IEvent;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class DossierClosed implements IEvent {
  private static final String EVENT_NAME = "DOSSIER_CLOSED";

  private String dossierId;
  private String uploadId;
  private String name;

  @Override
  public String getEventName() {
    return EVENT_NAME;
  }
}
