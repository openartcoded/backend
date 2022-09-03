package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.pages.client.BillableClient;
import tech.artcoded.websitev2.pages.client.BillableClientRepository;
import tech.artcoded.websitev2.pages.client.ContractStatus;
import tech.artcoded.websitev2.pages.invoice.InvoiceGeneration;
import tech.artcoded.websitev2.pages.invoice.InvoiceSearchCriteria;
import tech.artcoded.websitev2.pages.invoice.InvoiceService;

import java.io.IOException;
import java.util.Comparator;
import java.util.Date;
import java.util.stream.Collectors;


@Slf4j
@ChangeUnit(id = "fill-billable-client",
  order = "15",
  author = "Nordine Bittich")
public class $15_FillBillableClient {

  @RollbackExecution
  public void rollbackExecution() {
  }

  @Execution
  public void execute(InvoiceService invoiceService, BillableClientRepository billableClientRepository) throws IOException {
    if (billableClientRepository.count()==0) {
      var invoices = invoiceService.findAll(InvoiceSearchCriteria.builder()
        .archived(true)
        .build());
      invoices.stream().collect(Collectors.groupingBy(invoice -> invoice.getBillTo().getClientName()))
        .forEach((client, clientInvoices) -> {
          clientInvoices.stream().max(Comparator.comparing(InvoiceGeneration::getDateOfInvoice))
            .ifPresent(invoice -> {
                var billableClientBuilder = BillableClient.builder()
                  .address(invoice.getBillTo().getAddress())
                  .city(invoice.getBillTo().getCity())
                  .name(invoice.getBillTo().getClientName())
                  .vatNumber(invoice.getBillTo().getVatNumber())
                  .maxDaysToPay(invoice.getMaxDaysToPay())
                  .emailAddress(invoice.getBillTo().getEmailAddress());

                invoice.getInvoiceTable().stream().findFirst().ifPresent(row -> {
                  billableClientBuilder.rate(row.getRate()).rateType(row.getRateType());
                  billableClientBuilder.projectName(row.getProjectName());
                });

                billableClientBuilder.contractStatus(ContractStatus.ONGOING);
                billableClientBuilder.startDate(new Date());
                var billableClient = billableClientBuilder.build();
                log.info("saving {}", billableClient);
                billableClientRepository.save(billableClient);
                log.info("saved {}", billableClient.getName());

              }
            );
        });

    } else {
      log.info("billable client already filled!");
    }

  }

}
