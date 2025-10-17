
package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import tech.artcoded.websitev2.pages.fee.FeeRepository;

import java.io.IOException;

@ChangeUnit(id = "add-bookmarked-to-fee", order = "50", author = "Nordine Bittich")
public class CHANGE_LOG_50_AddBookmarkedToFee {

    @RollbackExecution
    public void rollbackExecution() {
    }

    @Execution
    public void execute(FeeRepository repository) throws IOException {
        repository.findAll().stream().map(f -> f.toBuilder().bookmarked(false).build()).forEach(repository::save);

    }

}
