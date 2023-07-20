package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import tech.artcoded.websitev2.pages.invoice.InvoiceService;
import tech.artcoded.websitev2.rest.util.MockMultipartFile;

import java.io.IOException;

import org.apache.commons.io.IOUtils;
import org.springframework.core.io.ClassPathResource;

@ChangeUnit(id = "add-invoice-template-end-2022", order = "28", author = "Nordine Bittich")
public class CHANGE_LOG_28_AddInvoiceTemplateEnd2022 {

  @RollbackExecution
  public void rollbackExecution() {
  }

  @Execution
  public void execute(InvoiceService service) throws IOException {
    var templateRes = new ClassPathResource("invoice/template-end-2022.ftl");
    try (var is = templateRes.getInputStream()) {
      service.addTemplate("Template End 2022", MockMultipartFile.builder()
          .contentType("application/octet-stream")
          .name("template-end-2022.ftl")
          .originalFilename("template-end-2022.ftl")
          .bytes(IOUtils.toByteArray(is))
          .build());
    }

  }

}
