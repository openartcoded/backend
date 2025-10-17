package tech.artcoded.event.v1.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class AdministrativeDocumentRemoved implements IDocumentEvent {
    private String documentId;

}
