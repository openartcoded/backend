package tech.artcoded.websitev2.peppol;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.remote.SftpComponent;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.processor.idempotent.FileIdempotentRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;

import com.helger.phive.api.executorset.ValidationExecutorSetRegistry;
import com.helger.phive.peppol.PeppolValidation2025_05;
import com.helger.phive.xml.source.IValidationSourceXML;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Date;
import java.util.List;

import org.apache.camel.Body;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.springframework.beans.factory.annotation.Value;

import lombok.SneakyThrows;
import tech.artcoded.websitev2.pages.fee.FeeService;
import tech.artcoded.websitev2.pages.invoice.InvoiceGenerationRepository;
import tech.artcoded.websitev2.rest.util.MockMultipartFile;

@Configuration
public class PeppolRouteBuilder extends RouteBuilder {
  @Value("${application.upload.peppolFTPUser}")
  private String peppolFTPUser;

  @Value("${application.upload.peppolFTP}")
  private String peppolFTPURI;

  @Value("${application.upload.peppolFTPHostKey}")
  private String pathToPeppolFTPHostKey;
  @Value("${application.upload.expenseIdempotentFilePath}")
  private String expenseIdempotentFilePath;

  @Value("${application.upload.successInvoiceIdempotentFilePath}")
  private String successInvoiceIdempotentFilePath;
  private final FeeService feeService;
  private final InvoiceGenerationRepository invoiceRepository;

  public PeppolRouteBuilder(
      FeeService feeService,
      InvoiceGenerationRepository invoiceRepository) {
    this.invoiceRepository = invoiceRepository;
    this.feeService = feeService;
  }

  @Bean("expenseIdempotent")
  public IdempotentRepository expenseIdempotent() {
    return FileIdempotentRepository.fileIdempotentRepository(new java.io.File(expenseIdempotentFilePath));
  }

  @Bean("successInvoiceIdempotent")
  public IdempotentRepository successInvoiceIdempotent() {
    return FileIdempotentRepository.fileIdempotentRepository(new java.io.File(successInvoiceIdempotentFilePath));
  }

  @Bean
  @SneakyThrows
  public SftpComponent sftpComponent(CamelContext camelContext) {
    SftpComponent sftp = new SftpComponent();
    sftp.setCamelContext(camelContext);
    return sftp;
  }

  @Bean
  public ValidationExecutorSetRegistry<IValidationSourceXML> registry() {
    final ValidationExecutorSetRegistry<IValidationSourceXML> registry = new ValidationExecutorSetRegistry<>();
    PeppolValidation2025_05.init(registry);
    return registry;
  }

  void updatePeppolStatus(@Header(Exchange.FILE_NAME) String fileName,
      @Header(Exchange.FILE_CONTENT_TYPE) String contentType) throws IOException {
    if (!MediaType.TEXT_XML_VALUE.equals(contentType) && !MediaType.APPLICATION_XML.equals(contentType)) {
      log.error("invoice is not of type xml: " + fileName);
      return;
    }
    var invoiceId = fileName.replace(".xml", "");
    invoiceRepository.findById(invoiceId).map(i -> i.toBuilder().peppolStatus(PeppolStatus.SUCCESS).build())
        .ifPresentOrElse(invoiceRepository::save,
            () -> log.error(fileName + ": cannot extract invoice id or invoice doesn't exist."));

  }

  @SneakyThrows
  void pushFee(@Body File file, @Header(Exchange.FILE_NAME) String fileName,
      @Header(Exchange.FILE_CONTENT_TYPE) String contentType) {

    if (!MediaType.TEXT_XML_VALUE.equals(contentType) && !MediaType.APPLICATION_XML.equals(contentType)) {
      log.error("expense is not of type xml: " + fileName);
    }

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
    fromF(
        "%s/invoices/Succes?username=%s&privateKeyFile=%s&delete=false&strictHostKeyChecking=no&useUserKnownHostsFile=false&autoCreate=true&noop=true&idempotentRepository=#successInvoiceIdempotent&recursive=true",
        peppolFTPURI,
        peppolFTPUser,
        pathToPeppolFTPHostKey)
        .routeId("Peppol::UpdateProcessedInvoices")
        .log("receiving file '${headers.%s}', will update peppol status".formatted(Exchange.FILE_NAME))
        .bean(() -> this, "updatePeppolStatus");
    fromF(
        "%s/expenses?username=%s&privateKeyFile=%s&delete=false&strictHostKeyChecking=no&useUserKnownHostsFile=false&autoCreate=true&noop=true&idempotentRepository=#expenseIdempotent&recursive=true",
        peppolFTPURI,
        peppolFTPUser,
        pathToPeppolFTPHostKey)
        .routeId("Peppol::PeppolExpenseToFee")
        .log("receiving file '${headers.%s}', will convert it to fee".formatted(Exchange.FILE_NAME))
        .bean(() -> this, "pushFee");
  }

}
