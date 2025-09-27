package tech.artcoded.websitev2.peppol;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.remote.SftpComponent;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.processor.idempotent.FileIdempotentRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import com.helger.phive.api.executorset.ValidationExecutorSetRegistry;
import com.helger.phive.peppol.PeppolValidation2025_05;
import com.helger.phive.xml.source.IValidationSourceXML;

import static java.net.URLConnection.guessContentTypeFromName;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

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

  void updatePeppolStatus(@Header(Exchange.FILE_NAME) String fileName) throws IOException {
    if (!fileName.endsWith(".xml")) {
      log.error("invoice is not of type xml: " + fileName);
      return;
    }
    String baseName = Paths.get(fileName).getFileName().toString();
    String invoiceId = baseName.substring(0, baseName.length() - 4); // remove .xml
    log.info("invoice with id " + invoiceId + " will be set to success");
    invoiceRepository.findById(invoiceId).map(i -> i.toBuilder().peppolStatus(PeppolStatus.SUCCESS).build())
        .ifPresentOrElse(invoiceRepository::save,
            () -> log.error(fileName + ": cannot extract invoice id or invoice doesn't exist."));

  }

  @SneakyThrows
  void pushFee(@Body byte[] fileBytes, @Header(Exchange.FILE_NAME) String fileName) {

    try {
      if (!fileName.endsWith(".xml")) {
        log.error("expense is not of type xml: " + fileName);
      }

      var fileXML = new String(fileBytes, StandardCharsets.UTF_8);

      MockMultipartFile multipartFile = MockMultipartFile.builder()
          .originalFilename(fileName)
          .contentType(Optional.ofNullable(guessContentTypeFromName(fileName)).orElse(MediaType.TEXT_PLAIN_VALUE))
          .name(fileName)
          .bytes(fileXML.trim().getBytes(StandardCharsets.UTF_8))
          .build();
      var subject = "[PEPPOL]: " + fileName;
      var description = "New peppol expense";
      var issueDate = new Date();
      List<MultipartFile> attachments = new ArrayList<>();
      attachments.add(multipartFile);

      var metadata = PeppolParserUtil.tryParse(multipartFile.getBytes());
      if (metadata.isPresent()) {
        var meta = metadata.get();
        subject = Optional.ofNullable(meta.getSupplierName()).orElse(subject);
        description = Optional.ofNullable(meta.getDescription())
            .orElse(Optional.ofNullable(meta.getId()).orElse(description));
        issueDate = Optional.ofNullable(meta.getIssueDate()).orElse(issueDate);
        attachments.addAll(meta.getAttachments());
      }
      var fee = this.feeService.save(subject, description, issueDate, attachments);
      if (metadata.isPresent()) {
        var meta = metadata.get();
        final BigDecimal taxes = Optional.ofNullable(meta.getTaxAmount())
            .orElseGet(() -> BigDecimal.ZERO);

        // set tag
        Optional.ofNullable(meta.getTaxExclusiveAmount())
            .ifPresent(priceHVat -> {
              this.feeService.updatePrice(fee.getId(), priceHVat, taxes);
              this.feeService.updateTag("PEPPOL", List.of(fee.getId()));
            });

      }
    } catch (Exception ex) {
      log.error("could not process fee", ex);

    }

  }

  @Override
  public void configure() throws Exception {
    onException(Exception.class)
        .handled(true)
        .transform().simple("Exception occurred due: ${exception}")
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
        "%s/expenses?username=%s&privateKeyFile=%s&delete=false&strictHostKeyChecking=no&useUserKnownHostsFile=false&autoCreate=true&noop=true&idempotentRepository=#expenseIdempotent&recursive=true&download=true",
        peppolFTPURI,
        peppolFTPUser,
        pathToPeppolFTPHostKey)
        .routeId("Peppol::PeppolExpenseToFee")
        .log("receiving file '${headers.%s}', will convert it to fee".formatted(Exchange.FILE_NAME))
        .bean(() -> this, "pushFee");
  }

}
