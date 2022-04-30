package tech.artcoded.event.v1.dossier;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.artcoded.event.IEvent;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class DossierDeleted implements IEvent {

  private String dossierId;

  @Override
  public Version getVersion() {
    return Version.V1;
  }
}
