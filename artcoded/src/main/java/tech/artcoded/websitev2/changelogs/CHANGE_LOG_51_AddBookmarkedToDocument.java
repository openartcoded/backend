
package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import tech.artcoded.websitev2.pages.document.AdministrativeDocumentRepository;

import java.io.IOException;

@ChangeUnit(id = "add-bookmarked-to-document", order = "51", author = "Nordine Bittich")
public class CHANGE_LOG_51_AddBookmarkedToDocument {

  @RollbackExecution
  public void rollbackExecution() {
  }

  @Execution
  public void execute(AdministrativeDocumentRepository repository) throws IOException {
    repository.findAll().stream().map(d -> d.toBuilder().bookmarked(false).build())
        .forEach(repository::save);

  }

}
