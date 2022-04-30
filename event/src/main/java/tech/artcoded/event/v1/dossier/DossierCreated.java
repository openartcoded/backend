package tech.artcoded.event.v1.dossier;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.artcoded.event.IEvent;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class DossierCreated implements IEvent {
  private String dossierId;
  private String name;
  private String description;
  private BigDecimal tvaDue;

  @Override
  public Version getVersion() {
    return Version.V1;
  }

}
