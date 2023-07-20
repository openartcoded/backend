package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import tech.artcoded.websitev2.pages.invoice.InvoiceGenerationRepository;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;

@ChangeUnit(id = "fix-period-invoice", order = "34", author = "Nordine Bittich")
public class CHANGE_LOG_34_FixPeriodInvoice {

  @RollbackExecution
  public void rollbackExecution() {
  }

  @Execution
  public void execute(InvoiceGenerationRepository repository) throws IOException {
    repository.findById("8a83a2cb-f17e-4cf5-a2fd-8699ee22edb9").ifPresent(invoice -> {
      var row = invoice.getInvoiceTable().get(0);
      if (row != null && StringUtils.isNotEmpty(row.getPeriod())) {
        var period = row.getPeriod().split("-");
        if (period.length == 2) {
          row.setPeriod(period[1] + "/" + period[0]);
          repository.save(invoice);
        }
      }
    });

  }

}
