package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.pages.invoice.InvoiceGenerationRepository;

import java.io.IOException;

@Slf4j
@ChangeUnit(id = "add-empty-if-null-in-invoice", order = "26", author = "Nordine Bittich")
@SuppressWarnings("deprecation")
public class $26_AddEmptyIfNullInInvoice {

  @RollbackExecution
  public void rollbackExecution() {
  }

  @Execution
  public void execute(InvoiceGenerationRepository invoiceRepository) throws IOException {
    var invoices = invoiceRepository.findAll();
    for (var invoice : invoices) {
      var builder = invoice.toBuilder();
      var changed = false;
      if (invoice.getSpecialNote() == null) {
        builder = builder.specialNote("");
        changed = true;
      }
      var billTo = invoice.getBillTo();
      if (billTo == null) {
        log.warn("invoice {} has en empty bill to", invoice.getInvoiceNumber());
      } else {
        if (billTo.getEmailAddress() == null) {
          log.warn("invoice {} has an empty email address", invoice.getInvoiceNumber());
          builder = builder.billTo(billTo.toBuilder().emailAddress("").build());
          changed = true;
        }
      }
      if (changed) {
        log.warn("invoice {} has changed", invoice.getInvoiceNumber());
        invoiceRepository.save(builder.build());
      }
    }

  }

}
