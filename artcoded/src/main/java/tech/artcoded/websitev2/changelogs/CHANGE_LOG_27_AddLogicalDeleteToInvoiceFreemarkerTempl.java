package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import tech.artcoded.websitev2.pages.invoice.InvoiceTemplateRepository;

import java.io.IOException;

@ChangeUnit(id = "add-logical-delete-to-invoice-freemarker", order = "27", author = "Nordine Bittich")
public class CHANGE_LOG_27_AddLogicalDeleteToInvoiceFreemarkerTempl {

  @RollbackExecution
  public void rollbackExecution() {
  }

  @Execution
  public void execute(InvoiceTemplateRepository repository) throws IOException {
    repository.findAll().stream().map(templ -> templ.toBuilder().logicalDelete(false).build())
        .forEach(repository::save);

  }

}
