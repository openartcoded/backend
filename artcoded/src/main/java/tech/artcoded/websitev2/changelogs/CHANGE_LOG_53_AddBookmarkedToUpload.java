
package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import tech.artcoded.websitev2.upload.FileUploadRepository;

import java.io.IOException;

@ChangeUnit(id = "add-bookmarked-to-upload", order = "53", author = "Nordine Bittich")
public class CHANGE_LOG_53_AddBookmarkedToUpload {

    @RollbackExecution
    public void rollbackExecution() {
    }

    @Execution
    public void execute(FileUploadRepository repository) throws IOException {
        repository.findAll().stream().map(f -> f.toBuilder().bookmarked(false).build()).forEach(repository::save);

    }

}
