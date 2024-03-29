package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import tech.artcoded.websitev2.pages.invoice.InvoiceGenerationRepository;
import tech.artcoded.websitev2.pages.invoice.seq.InvoicePrimarySequenceService;

import java.io.IOException;

@ChangeUnit(id = "init-invoice-seq", order = "36", author = "Nordine Bittich")
public class CHANGE_LOG_36_InitInvoiceSeq {

  @RollbackExecution
  public void rollbackExecution() {
  }

  @Execution
  public void execute(InvoicePrimarySequenceService service, InvoiceGenerationRepository repo) throws IOException {
    if (service.getCurrent() == null) {
      service.setValueTo(14);
    }

  }

}
