package tech.artcoded.websitev2.pages.dossier;

import lombok.extern.slf4j.Slf4j;
import net.lingala.zip4j.ZipFile;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tech.artcoded.websitev2.api.helper.IdGenerators;
import tech.artcoded.websitev2.pages.fee.Fee;
import tech.artcoded.websitev2.pages.fee.FeeRepository;
import tech.artcoded.websitev2.pages.fee.Tag;
import tech.artcoded.websitev2.pages.invoice.InvoiceGeneration;
import tech.artcoded.websitev2.pages.invoice.InvoiceGenerationRepository;
import tech.artcoded.websitev2.rest.util.MockMultipartFile;
import tech.artcoded.websitev2.upload.FileUploadService;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.apache.commons.io.FileUtils.readFileToByteArray;
import static tech.artcoded.websitev2.api.func.CheckedSupplier.toSupplier;
import static tech.artcoded.websitev2.api.func.CheckedVoidConsumer.toConsumer;

@Service
@Slf4j
public class CloseActiveDossierService {
  private final FileUploadService fileUploadService;
  private final DossierRepository dossierRepository;
  private final FeeRepository feeRepository;
  private final InvoiceGenerationRepository invoiceGenerationRepository;
  private final XlsReportService xlsReportService;

  public CloseActiveDossierService(FileUploadService fileUploadService,
                                   DossierRepository dossierRepository,
                                   FeeRepository feeRepository,
                                   InvoiceGenerationRepository invoiceGenerationRepository,
                                   XlsReportService xlsReportService) {
    this.fileUploadService = fileUploadService;
    this.dossierRepository = dossierRepository;
    this.feeRepository = feeRepository;
    this.invoiceGenerationRepository = invoiceGenerationRepository;
    this.xlsReportService = xlsReportService;
  }

  public Dossier closeActiveDossier() {
    return dossierRepository.findOneByClosedIsFalse().stream()
                            .map(
                                    dossier -> {
                                      File tempDir = new File(FileUtils.getTempDirectory(), IdGenerators.get());
                                      var created = tempDir.mkdir();
                                      File feeDir = new File(tempDir, "expenses");
                                      feeDir.mkdir();
                                      File invoiceDir = new File(tempDir, "invoices");
                                      invoiceDir.mkdir();
                                      var invoices = dossier.getInvoiceIds()
                                                            .stream().map(invoiceGenerationRepository::findById)
                                                            .flatMap(Optional::stream).collect(Collectors.toList());
                                      Map<Tag, List<Fee>> feesPerTag =
                                              dossier.getFeeIds().stream()
                                                     .map(feeRepository::findById)
                                                     .flatMap(Optional::stream)
                                                     .filter(f -> Objects.nonNull(f.getTag()))
                                                     .collect(Collectors.groupingBy(Fee::getTag));
                                      feesPerTag.forEach(
                                              (key, value) -> {
                                                File tagDir = new File(feeDir, Tag.label(key));
                                                var ok = tagDir.mkdir();
                                                value.stream()
                                                     .flatMap(fee -> fee.getAttachmentIds().stream())
                                                     .map(fileUploadService::findOneById)
                                                     .flatMap(Optional::stream)
                                                     .forEach(
                                                             upl ->
                                                                     toSupplier(
                                                                             () -> {
                                                                               File tempFile = new File(tagDir, upl.getFilename());
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
                                                                                                   File tempFile = new File(invoiceDir, upl.getFilename());
                                                                                                   FileUtils.writeByteArrayToFile(
                                                                                                           tempFile, fileUploadService.uploadToByteArray(upl));
                                                                                                   return tempFile;
                                                                                                 })
                                                                                                 .get());
                                      File tempZip = new File(FileUtils.getTempDirectory(), IdGenerators.get().concat(".zip"));

                                      toConsumer(() -> new ZipFile(tempZip).addFolder(tempDir)).safeConsume();

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
                                                             .closedDate(new Date())
                                                             .updatedDate(new Date())
                                                             .build();
                                      toConsumer(() -> {
                                        FileUtils.deleteDirectory(tempDir);
                                        FileUtils.forceDelete(tempZip);
                                      }).safeConsume();
                                      return this.dossierRepository.save(
                                              build.toBuilder()
                                                   .dossierUploadId(fileUploadService.upload(multipartFile, dossier.getId(), false))
                                                   .build()
                                      );
                                    })
                            .findFirst()
                            .orElseThrow(() -> new RuntimeException("no active dossier"));
  }

}
