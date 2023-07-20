package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import tech.artcoded.websitev2.pages.invoice.InvoiceGenerationRepository;

import java.io.IOException;

@ChangeUnit(id = "set-seq-to-minus-one-for-old-inv", order = "38", author = "Nordine Bittich")
public class CHANGE_LOG_38_SetSeqToMinusOneForOldInv {

  @RollbackExecution
  public void rollbackExecution() {
  }

  @Execution
  public void execute(InvoiceGenerationRepository repo) throws IOException {
    repo.findAll().stream().filter(i -> i.getSeqInvoiceNumber() == null)
        .forEach(i -> repo.save(i.toBuilder().seqInvoiceNumber(-1L).build()));

  }

}
