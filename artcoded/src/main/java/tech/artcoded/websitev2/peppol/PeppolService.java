package tech.artcoded.websitev2.peppol;

import java.io.File;
import java.nio.file.Files;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.pages.invoice.InvoiceGeneration;
import tech.artcoded.websitev2.pages.invoice.InvoiceGenerationRepository;
import tech.artcoded.websitev2.upload.FileUploadService;

@Service
@Slf4j
public class PeppolService {

  @Value("${application.upload.pathToPeppolExpenditure}")
  private String pathToPeppolExpenditure;

  @Value("${application.upload.pathToPeppolInvoice}")
  private String pathToPeppolInvoice;

  private final FileUploadService uploadService;
  private final InvoiceGenerationRepository invoiceRepository;

  public PeppolService(FileUploadService uploadService,
      InvoiceGenerationRepository invoiceRepository) {
    this.uploadService = uploadService;
    this.invoiceRepository = invoiceRepository;
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

}
