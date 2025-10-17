
package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import tech.artcoded.websitev2.pages.dossier.DossierRepository;

import java.io.IOException;

@ChangeUnit(id = "add-bookmarked-to-dossier", order = "52", author = "Nordine Bittich")
public class CHANGE_LOG_52_AddBookmarkedToDossier {

    @RollbackExecution
    public void rollbackExecution() {
    }

    @Execution
    public void execute(DossierRepository repository) throws IOException {
        repository.findAll().stream().map(d -> d.toBuilder().bookmarked(false).build()).forEach(repository::save);

    }

}
