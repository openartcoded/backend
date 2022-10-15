package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.pages.invoice.InvoiceFreemarkerTemplate;
import tech.artcoded.websitev2.pages.invoice.InvoiceGenerationRepository;
import tech.artcoded.websitev2.pages.invoice.InvoiceTemplateRepository;

import java.io.IOException;

@Slf4j
@ChangeUnit(id = "add-logical-delete-to-invoice-freemarker", order = "27", author = "Nordine Bittich")
public class $27_AddLogicalDeleteToInvoiceFreemarkerTempl {

  @RollbackExecution
  public void rollbackExecution() {
  }

  @Execution
  public void execute(InvoiceTemplateRepository repository) throws IOException {
    repository.findAll().stream().map(templ -> templ.toBuilder().logicalDelete(false).build())
    .forEach(repository::save);

  }

}
