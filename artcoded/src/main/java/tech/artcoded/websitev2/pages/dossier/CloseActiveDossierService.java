package tech.artcoded.websitev2.pages.dossier;

import lombok.extern.slf4j.Slf4j;
import net.lingala.zip4j.ZipFile;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import tech.artcoded.websitev2.pages.document.AdministrativeDocument;
import tech.artcoded.websitev2.pages.document.AdministrativeDocumentService;
import tech.artcoded.websitev2.pages.fee.Fee;
import tech.artcoded.websitev2.pages.fee.FeeRepository;
import tech.artcoded.websitev2.pages.invoice.InvoiceGeneration;
import tech.artcoded.websitev2.pages.invoice.InvoiceService;
import tech.artcoded.websitev2.rest.util.MockMultipartFile;
import tech.artcoded.websitev2.upload.IFileUploadService;
import tech.artcoded.websitev2.utils.helper.DateHelper;
import tech.artcoded.websitev2.utils.helper.IdGenerators;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.commons.io.FileUtils.readFileToByteArray;
import static tech.artcoded.websitev2.utils.func.CheckedSupplier.toSupplier;
import static tech.artcoded.websitev2.utils.func.CheckedVoidConsumer.toConsumer;

@Service
@Slf4j
public class CloseActiveDossierService {
    private final IFileUploadService fileUploadService;
    private final AdministrativeDocumentService documentService;
    private final DossierRepository dossierRepository;
    private final FeeRepository feeRepository;
    private final InvoiceService invoiceService;
    private final XlsReportService xlsReportService;
    private final ProcessAttachmentToDossierService processAttachmentToDossierService;

    public CloseActiveDossierService(IFileUploadService fileUploadService, DossierRepository dossierRepository,
            FeeRepository feeRepository, ProcessAttachmentToDossierService processAttachmentToDossierService,
            AdministrativeDocumentService documentService, InvoiceService invoiceService,
            XlsReportService xlsReportService) {
        this.fileUploadService = fileUploadService;
        this.documentService = documentService;
        this.dossierRepository = dossierRepository;
        this.processAttachmentToDossierService = processAttachmentToDossierService;
        this.feeRepository = feeRepository;
        this.invoiceService = invoiceService;
        this.xlsReportService = xlsReportService;
    }

    @CacheEvict(cacheNames = { "activeDossier", "dossierSummaries", "dossierTotalSize",
            "dossierByFeeId" }, allEntries = true)
    public Dossier addExpenseToClosedDossier(String dossierId, List<String> attachmentIds) {
        log.info("sarching for dossier with id {}", dossierId);
        var dossier = dossierRepository.findById(dossierId)
                .orElseThrow(() -> new RuntimeException("dossier with id " + dossierId + " doesn't exist"));
        log.info("searching for expense with ids {}", attachmentIds);
        attachmentIds.forEach(attachmentId -> feeRepository.findById(attachmentId)
                .orElseThrow(() -> new RuntimeException("attachment with id " + attachmentId + " doesn't exist")));
        var updatedDossier = this.processAttachmentToDossierService
                .processFeesForDossier(Optional.of(dossier),
                        Stream.concat(dossier.getFeeIds().stream(), attachmentIds.stream()).toList())
                .orElseThrow(() -> new RuntimeException("could not add fee to dossier. dossier is empty."));
        return this.closeDossier(updatedDossier.toBuilder()
                .name(updatedDossier.getName() + "-FIXED" + DateHelper.getDD_MM_YYYY(new Date()))
                .description("%s [updated with attachmentIds %s]".formatted(dossier.getDescription(),
                        attachmentIds.stream().collect(Collectors.joining(","))))
                .build(), new Date());
    }

    @CacheEvict(cacheNames = { "activeDossier", "dossierSummaries", "dossierTotalSize",
            "dossierByFeeId" }, allEntries = true)
    public Dossier closeDossier(Dossier dossier, Date closedDate) {
        Optional.ofNullable(dossier.getDossierUploadId()).flatMap(u -> fileUploadService.findOneById(u))
                .ifPresent(upload -> {
                    log.info("dossier was already closed at some point. Zip exist {}. process to delete upload {}",
                            upload.getName(), upload.getId());
                    fileUploadService.delete(upload);
                });
        File tempDir = new File(FileUtils.getTempDirectory(), IdGenerators.get());
        log.info("tempDir.mkdir() {}", tempDir.mkdir());
        File feeDir = new File(tempDir, "expenses");
        log.debug("feeDir.mkdir {}", feeDir.mkdir());
        File invoiceDir = new File(tempDir, "invoices");
        log.debug("invoiceDir.mkdir() {}", invoiceDir.mkdir());
        File documentDir = new File(tempDir, "documents");
        log.debug("documentDir.mkdir() {}", documentDir.mkdir());

        var documents = documentService.findAll(dossier.getDocumentIds());
        var invoices = invoiceService.findAll(dossier.getInvoiceIds());
        Map<String, List<Fee>> feesPerTag = feeRepository.findAllById(dossier.getFeeIds()).stream()
                .filter(f -> Objects.nonNull(f.getTag())).collect(Collectors.groupingBy(Fee::getTag));
        feesPerTag.forEach((key, value) -> {
            File tagDir = new File(feeDir, FilenameUtils.normalize(key.toLowerCase()));
            log.info("create directory: {}", tagDir.mkdir());
            var attachmentIds = value.stream().flatMap(fee -> fee.getAttachmentIds().stream()).toList();
            fileUploadService.findAll(attachmentIds).forEach(upl -> toSupplier(() -> {
                File tempFile = new File(tagDir, upl.getOriginalFilename());
                FileUtils.writeByteArrayToFile(tempFile, fileUploadService.uploadToByteArray(upl));
                return tempFile;
            }).get());
        });
        Optional<MultipartFile> summaryReport = xlsReportService.generate(dossier, invoices, feesPerTag);
        summaryReport.ifPresent(s -> {
            log.info("add xlsx summary report to dossier");
            toConsumer(() -> s.transferTo(tempDir)).consume(e -> log.error("error: ", e));
        });
        var invoiceUploadIds = invoices.stream().map(InvoiceGeneration::getInvoiceUploadId).toList();
        fileUploadService.findAll(invoiceUploadIds).forEach(upl -> toSupplier(() -> {
            File tempFile = new File(invoiceDir, upl.getOriginalFilename());
            FileUtils.writeByteArrayToFile(tempFile, fileUploadService.uploadToByteArray(upl));
            return tempFile;
        }).get());

        var documentUploadIds = documents.stream().map(AdministrativeDocument::getAttachmentId).toList();
        fileUploadService.findAll(documentUploadIds).forEach(upl -> toSupplier(() -> {
            File tempFile = new File(documentDir, upl.getOriginalFilename());
            FileUtils.writeByteArrayToFile(tempFile, fileUploadService.uploadToByteArray(upl));
            return tempFile;
        }).get());

        File tempZip = new File(FileUtils.getTempDirectory(), IdGenerators.get().concat(".zip"));

        try (var zipFile = new ZipFile(tempZip)) {
            zipFile.addFolder(tempDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String fileName = String.format("%s.zip", FilenameUtils.normalize(dossier.getName()));
        MockMultipartFile multipartFile = MockMultipartFile.builder().name(fileName).contentType("application/zip")
                .originalFilename(fileName).bytes(toSupplier(() -> readFileToByteArray(tempZip)).get()).build();
        Dossier build = dossier.toBuilder().closed(true).closedDate(closedDate).updatedDate(closedDate).build();
        toConsumer(() -> {
            FileUtils.deleteDirectory(tempDir);
            FileUtils.forceDelete(tempZip);
        }).safeConsume();
        return this.dossierRepository.save(build.toBuilder()
                .dossierUploadId(fileUploadService.upload(multipartFile, dossier.getId(), closedDate, false)).build());
    }

    @CacheEvict(cacheNames = { "activeDossier", "dossierSummaries", "dossierTotalSize",
            "dossierByFeeId" }, allEntries = true)
    public Dossier closeActiveDossier() {
        return dossierRepository.findOneByClosedIsFalse().stream()
                .map(dossier -> this.closeDossier(dossier, new Date())).findFirst()
                .orElseThrow(() -> new RuntimeException("no active dossier"));
    }

}
