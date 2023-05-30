package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import tech.artcoded.websitev2.pages.invoice.seq.InvoicePrimarySequenceService;

import java.io.IOException;

@ChangeUnit(id = "init-invoice-seq", order = "36", author = "Nordine Bittich")
public class $36_InitInvoiceSeq {

  @RollbackExecution
  public void rollbackExecution() {
  }

  @Execution
  public void execute(InvoicePrimarySequenceService service) throws IOException {
    if (service.getNextValueAndIncrementBy(0) != 1) {
      service.getNextValueAndIncrementBy(15);
    }

  }

}
