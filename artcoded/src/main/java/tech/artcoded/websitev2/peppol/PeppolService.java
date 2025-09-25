package tech.artcoded.websitev2.peppol;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Date;
import java.util.List;

import org.apache.camel.Body;
import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.pages.fee.FeeService;
import tech.artcoded.websitev2.pages.invoice.InvoiceGeneration;
import tech.artcoded.websitev2.pages.invoice.InvoiceGenerationRepository;
import tech.artcoded.websitev2.rest.util.MockMultipartFile;
import tech.artcoded.websitev2.upload.FileUploadService;

@Service
@Slf4j
public class PeppolService extends RouteBuilder {

  @Value("${application.upload.pathToPeppolExpenditure}")
  private String pathToPeppolExpenditure;

  @Value("${application.upload.pathToPeppolInvoice}")
  private String pathToPeppolInvoice;

  private final FileUploadService uploadService;
  private final FeeService feeService;
  private final InvoiceGenerationRepository invoiceRepository;

  public PeppolService(FileUploadService uploadService,
      FeeService feeService,
      InvoiceGenerationRepository invoiceRepository) {
    this.uploadService = uploadService;
    this.invoiceRepository = invoiceRepository;
    this.feeService = feeService;
  }

  @SneakyThrows
  public void addInvoice(InvoiceGeneration invoice) {
    log.info("receiving invoice {}. copying it to {} and set status to processing...", invoice.getId(),
        pathToPeppolInvoice);
    var ubl = this.uploadService.findOneById(invoice.getInvoiceUBLId())
        .orElseThrow(() -> new RuntimeException("file with id %s not found".formatted(invoice.getInvoiceUBLId())));

    var file = this.uploadService.getFile(ubl);
    var out = new File(pathToPeppolInvoice, "%s.xml".formatted(invoice.getId()));
    Files.copy(file.toPath(), out.toPath());
    this.invoiceRepository.findById(invoice.getId())
        .map(i -> i.toBuilder().peppolStatus(PeppolStatus.PROCESSING).build())
        .ifPresent(invoiceRepository::save);
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

    from("file:%s/success?noop=true&idempotent=true&idempotentRepository=#fileIdempotentRepository"
        .formatted(pathToPeppolInvoice))
        .routeId("Peppol::UpdateProcessedInvoices")
        .log("receiving file '${headers.%s}', will update peppol status".formatted(Exchange.FILE_NAME))
        .bean(() -> this, "updatePeppolStatus");

    from("file:" + pathToPeppolExpenditure)
        .routeId("Peppol::PeppolExpenseToFee")
        .log("receiving file '${headers.%s}', will convert it to fee".formatted(Exchange.FILE_NAME))
        .bean(() -> this, "pushFee");
  }
}
