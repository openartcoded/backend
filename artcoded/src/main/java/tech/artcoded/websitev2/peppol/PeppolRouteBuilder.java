package tech.artcoded.websitev2.peppol;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.processor.idempotent.FileIdempotentRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.helger.phive.api.executorset.ValidationExecutorSetRegistry;
import com.helger.phive.peppol.PeppolValidation;
import com.helger.phive.peppol.PeppolValidation2025_05;
import com.helger.phive.xml.source.IValidationSourceXML;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Date;
import java.util.List;

import org.apache.camel.Body;
import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.springframework.beans.factory.annotation.Value;

import lombok.SneakyThrows;
import tech.artcoded.websitev2.pages.fee.FeeService;
import tech.artcoded.websitev2.pages.invoice.InvoiceGenerationRepository;
import tech.artcoded.websitev2.rest.util.MockMultipartFile;

@Configuration
public class PeppolRouteBuilder extends RouteBuilder {
  @Value("${application.upload.pathToPeppolExpenditure}")
  private String pathToPeppolExpenditure;

  @Value("${application.upload.pathToPeppolInvoice}")
  private String pathToPeppolInvoice;

  @Value("${application.upload.pathToPeppolFTP}")
  private String pathToPeppolFTP;

  private final FeeService feeService;
  private final InvoiceGenerationRepository invoiceRepository;

  public PeppolRouteBuilder(
      FeeService feeService,
      InvoiceGenerationRepository invoiceRepository) {
    this.invoiceRepository = invoiceRepository;
    this.feeService = feeService;
  }

  @Bean("invoiceIdempotentRepository")
  public IdempotentRepository invoiceIdempotentRepository() {
    return FileIdempotentRepository.fileIdempotentRepository(new java.io.File(pathToPeppolFTP, "invoice_idempot"));
  }

  @Bean("expenseIdempotentRepository")
  public IdempotentRepository expenseIdempotentRepository() {
    return FileIdempotentRepository.fileIdempotentRepository(new java.io.File(pathToPeppolFTP, "expense_idempot"));
  }

  @Bean
  public ValidationExecutorSetRegistry<IValidationSourceXML> registry() {
    final ValidationExecutorSetRegistry<IValidationSourceXML> registry = new ValidationExecutorSetRegistry<>();
    PeppolValidation.initStandard(registry);
    return registry;
  }

  void updatePeppolStatus(@Header(Exchange.FILE_NAME) String fileName) throws IOException {
    var invoiceId = fileName.replace(".xml", "");
    invoiceRepository.findById(invoiceId).map(i -> i.toBuilder().peppolStatus(PeppolStatus.SUCCESS).build())
        .ifPresentOrElse(invoiceRepository::save,
            () -> log.error(fileName + ": cannot extract invoice id or invoice doesn't exist."));

  }

  @SneakyThrows
  void pushFee(@Body File file, @Header(Exchange.FILE_NAME) String fileName,
      @Header(Exchange.FILE_CONTENT_TYPE) String contentType) {

    this.feeService.save("[PEPPOL]: " + fileName, "New peppol expense", new Date(), List.of(MockMultipartFile.builder()
        .originalFilename(fileName)
        .contentType(contentType)
        .name(fileName)
        .bytes(Files.readAllBytes(file.toPath()))
        .build()));

  }

  @Override
  public void configure() throws Exception {
    onException(Exception.class)
        .handled(true)
        .transform().simple("Exception occurred due: ${exception.message}")
        .log("${body}");

    from("file:%s/success?noop=true&idempotent=true&idempotentRepository=#invoiceIdempotentRepository"
        .formatted(pathToPeppolInvoice))
        .routeId("Peppol::UpdateProcessedInvoices")
        .log("receiving file '${headers.%s}', will update peppol status".formatted(Exchange.FILE_NAME))
        .bean(() -> this, "updatePeppolStatus");
    from("file:%s?noop=true&idempotent=true&idempotentRepository=#expenseIdempotentRepository"
        .formatted(pathToPeppolExpenditure))
        .routeId("Peppol::PeppolExpenseToFee")
        .log("receiving file '${headers.%s}', will convert it to fee".formatted(Exchange.FILE_NAME))
        .bean(() -> this, "pushFee");
  }

}
