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
public class DossierUpdated implements IEvent {
  private String dossierId;
  private String name;

    @Override
    public Version getVersion() {
        return Version.V1;
    }
}
