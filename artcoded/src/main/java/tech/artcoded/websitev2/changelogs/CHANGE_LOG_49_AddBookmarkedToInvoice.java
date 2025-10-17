
package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import tech.artcoded.websitev2.pages.invoice.InvoiceGenerationRepository;

import java.io.IOException;

@ChangeUnit(id = "add-bookmarked-to-invoice", order = "49", author = "Nordine Bittich")
public class CHANGE_LOG_49_AddBookmarkedToInvoice {

    @RollbackExecution
    public void rollbackExecution() {
    }

    @Execution
    public void execute(InvoiceGenerationRepository repository) throws IOException {
        repository.findAll().stream().map(invoice -> invoice.toBuilder().bookmarked(false).build())
                .forEach(repository::save);

    }

}
