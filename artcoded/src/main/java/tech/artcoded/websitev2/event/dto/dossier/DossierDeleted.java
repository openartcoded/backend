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
public class DossierDeleted implements IEvent {
  private static final String EVENT_NAME = "DOSSIER_DELETED";

  private String dossierId;

  @Override
  public String getEventName() {
    return EVENT_NAME;
  }
}
