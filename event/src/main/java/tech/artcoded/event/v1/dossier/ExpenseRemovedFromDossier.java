package tech.artcoded.event.v1.dossier;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ExpenseRemovedFromDossier implements IDossierEvent {
    private String dossierId;
    private String expenseId;

    @Override
    public Version getVersion() {
        return Version.V1;
    }
}
