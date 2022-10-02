package tech.artcoded.websitev2.pages.dossier;

import lombok.extern.slf4j.Slf4j;
import net.lingala.zip4j.ZipFile;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tech.artcoded.websitev2.pages.fee.Fee;
import tech.artcoded.websitev2.pages.fee.FeeRepository;
import tech.artcoded.websitev2.pages.invoice.InvoiceGeneration;
import tech.artcoded.websitev2.pages.invoice.InvoiceService;
import tech.artcoded.websitev2.rest.util.MockMultipartFile;
import tech.artcoded.websitev2.upload.FileUploadService;
import tech.artcoded.websitev2.utils.helper.IdGenerators;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.apache.commons.io.FileUtils.readFileToByteArray;
import static tech.artcoded.websitev2.utils.func.CheckedSupplier.toSupplier;
import static tech.artcoded.websitev2.utils.func.CheckedVoidConsumer.toConsumer;

@Service
@Slf4j
public class CloseActiveDossierService {
  private final FileUploadService fileUploadService;
  private final DossierRepository dossierRepository;
  private final FeeRepository feeRepository;
  private final InvoiceService invoiceService;
  private final XlsReportService xlsReportService;

  public CloseActiveDossierService(FileUploadService fileUploadService,
                                   DossierRepository dossierRepository,
                                   FeeRepository feeRepository,
                                   InvoiceService invoiceService,
                                   XlsReportService xlsReportService) {
    this.fileUploadService = fileUploadService;
    this.dossierRepository = dossierRepository;
    this.feeRepository = feeRepository;
    this.invoiceService = invoiceService;
    this.xlsReportService = xlsReportService;
  }

  public Dossier closeDossier(Dossier dossier, Date closedDate) {
    File tempDir = new File(FileUtils.getTempDirectory(), IdGenerators.get());
    log.debug("tempDir.mkdir() {}", tempDir.mkdir());
    File feeDir = new File(tempDir, "expenses");
    log.debug("feeDir.mkdir {}", feeDir.mkdir());
    File invoiceDir = new File(tempDir, "invoices");
    log.debug("invoiceDir.mkdir() {}", invoiceDir.mkdir());
    var invoices = dossier.getInvoiceIds()
      .stream().map(invoiceService::findById)
      .flatMap(Optional::stream).collect(Collectors.toList());
    Map<String, List<Fee>> feesPerTag =
      dossier.getFeeIds().stream()
        .map(feeRepository::findById)
        .flatMap(Optional::stream)
        .filter(f -> Objects.nonNull(f.getTag()))
        .collect(Collectors.groupingBy(Fee::getTag));
    feesPerTag.forEach(
      (key, value) -> {
        File tagDir = new File(feeDir, FilenameUtils.normalize(key.toLowerCase()));
        var ok = tagDir.mkdir();
        value.stream()
          .flatMap(fee -> fee.getAttachmentIds().stream())
          .map(fileUploadService::findOneById)
          .flatMap(Optional::stream)
          .forEach(
            upl ->
              toSupplier(
                () -> {
                  File tempFile = new File(tagDir, upl.getOriginalFilename());
                  FileUtils.writeByteArrayToFile(
                    tempFile, fileUploadService.uploadToByteArray(upl));
                  return tempFile;
                })
                .get());
      });
    Optional<MultipartFile> summaryReport = xlsReportService.generate(dossier, invoices, feesPerTag);
    summaryReport.ifPresent(s -> {
      log.info("add xlsx summary report to dossier");
      toConsumer(() -> s.transferTo(tempDir)).consume(e -> log.error("error: ", e));
    });
    invoices.stream()
      .map(InvoiceGeneration::getInvoiceUploadId)
      .map(fileUploadService::findOneById)
      .flatMap(Optional::stream).forEach(upl ->
        toSupplier(
          () -> {
            File tempFile = new File(invoiceDir, upl.getOriginalFilename());
            FileUtils.writeByteArrayToFile(
              tempFile, fileUploadService.uploadToByteArray(upl));
            return tempFile;
          })
          .get());
    File tempZip = new File(FileUtils.getTempDirectory(), IdGenerators.get().concat(".zip"));

    try (var zipFile = new ZipFile(tempZip)) {
      zipFile.addFolder(tempDir);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    String fileName = String.format("%s.zip", FilenameUtils.normalize(dossier.getName()));
    MockMultipartFile multipartFile =
      MockMultipartFile.builder()
        .name(fileName)
        .contentType("application/zip")
        .originalFilename(fileName)
        .bytes(toSupplier(() -> readFileToByteArray(tempZip)).get())
        .build();
    Dossier build = dossier.toBuilder()
      .closed(true)
      .closedDate(closedDate)
      .updatedDate(closedDate)
      .build();
    toConsumer(() -> {
      FileUtils.deleteDirectory(tempDir);
      FileUtils.forceDelete(tempZip);
    }).safeConsume();
    return this.dossierRepository.save(
      build.toBuilder()
        .dossierUploadId(fileUploadService.upload(multipartFile, dossier.getId(), closedDate, false))
        .build()
    );
  }

  public Dossier closeActiveDossier() {
    return dossierRepository.findOneByClosedIsFalse().stream()
      .map(dossier -> this.closeDossier(dossier, new Date()))
      .findFirst()
      .orElseThrow(() -> new RuntimeException("no active dossier"));
  }

}
