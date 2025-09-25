
package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.io.IOException;

import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.pages.invoice.InvoiceGenerationRepository;
import tech.artcoded.websitev2.peppol.PeppolStatus;

@Slf4j
@ChangeUnit(id = "add-peppol-status", order = "46", author = "Nordine Bittich")
public class CHANGE_LOG_46_AddPeppolStatus {

  @RollbackExecution
  public void rollbackExecution() {
  }

  @Execution
  public void execute(InvoiceGenerationRepository repo)
      throws IOException {
    var invoices = repo.findAll().stream().map(i -> i.toBuilder().peppolStatus(PeppolStatus.OLD).build()).toList();
    repo.saveAll(invoices);
  }
}
