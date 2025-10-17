package tech.artcoded.websitev2.pages.dossier;

import org.apache.commons.io.IOUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import tech.artcoded.websitev2.rest.util.MockMultipartFile;
import tech.artcoded.websitev2.rest.util.RestUtil;

import javax.inject.Inject;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static tech.artcoded.websitev2.utils.func.CheckedSupplier.toSupplier;

@RestController
@RequestMapping("/api/dossier")
public class DossierController {
    private final DossierService dossierService;
    private final XlsReportService xlsReportService;
    private final ImportOldDossierService importOldDossierService;
    private final ProcessAttachmentToDossierService processAttachmentToDossierService;
    private ResponseEntity<ByteArrayResource> importDossierXlsxExample;

    @Inject
    public DossierController(DossierService dossierService, XlsReportService xlsReportService,
            ProcessAttachmentToDossierService processAttachmentToDossierService,
            ImportOldDossierService importOldDossierService) {
        this.dossierService = dossierService;
        this.processAttachmentToDossierService = processAttachmentToDossierService;
        this.xlsReportService = xlsReportService;
        this.importOldDossierService = importOldDossierService;
    }

    @PostMapping("/find-all-paged")
    public Page<Dossier> findAllPaged(@RequestParam(value = "closed", defaultValue = "false") boolean closed,
            Pageable pageable) {
        return dossierService.findAll(closed, pageable);
    }

    @PostMapping("/find-all")
    public List<Dossier> findAll(@RequestParam(value = "closed", defaultValue = "false") boolean closed) {
        return dossierService.findAll(closed);
    }

    @PostMapping("/bookmarked")
    public Page<Dossier> bookmarked(Pageable pageable) {
        return dossierService.bookmarked(pageable);
    }

    @PostMapping("/toggle-bookmarked")
    public ResponseEntity<Dossier> toggleBookmarked(@RequestParam("id") String id) {
        return dossierService.toggleBookmarked(id).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/summary")
    public DossierSummary getSummary(@RequestParam(value = "id") String id) {
        return dossierService.getSummary(id);
    }

    @PostMapping("/summaries")
    public List<DossierSummary> getSummaries(@RequestParam(value = "id") List<String> ids) {
        return dossierService.getSummaries(ids);
    }

    @PostMapping("/find-all-summaries")
    public List<DossierSummary> getSummaries(@RequestParam(value = "closed", defaultValue = "false") boolean closed) {
        return dossierService.getAllSummaries(closed);
    }

    @PostMapping("/new-from-previous")
    public Dossier newFromPrevious() {
        return dossierService.fromPreviousDossier();
    }

    @PostMapping("/find-by-id")
    public ResponseEntity<Dossier> findById(@RequestParam("id") String id) {
        return dossierService.findById(id).map(ResponseEntity::ok).orElseGet(ResponseEntity.noContent()::build);
    }

    @PostMapping("/size")
    public ResponseEntity<Long> getDossierTotalSize(@RequestParam("id") String id) {
        return dossierService.getDossierTotalSize(id).map(ResponseEntity::ok)
                .orElseGet(ResponseEntity.noContent()::build);
    }

    @GetMapping("/generate-summary")
    public ResponseEntity<ByteArrayResource> generateSummary(@RequestParam("id") String id) {
        Optional<MultipartFile> summary = xlsReportService.generate(id);
        return summary.map(s -> RestUtil.transformToByteArrayResource(s.getOriginalFilename(), s.getContentType(),
                toSupplier(s::getBytes).get())).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/find-by-fee-id")
    public ResponseEntity<Dossier> findByFeeId(@RequestParam("id") String id) {
        return dossierService.findByFeeId(id).map(ResponseEntity::ok).orElseGet(ResponseEntity.noContent()::build);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public void importDossierFromZip(@RequestPart(value = "zip") MultipartFile zip) {
        importOldDossierService.create(MockMultipartFile.copy(zip));
    }

    @GetMapping("/import-example")
    public ResponseEntity<ByteArrayResource> getImportDossierXlsxExample() {
        return importDossierXlsxExample;
    }

    @PostMapping("/process-fees")
    public ResponseEntity<Void> processFeesForDossier(@RequestBody List<String> feeIds) {
        this.processAttachmentToDossierService.processFeesForDossier(this.dossierService.getActiveDossier(), feeIds);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/add-document")
    public ResponseEntity<Void> addDocumentToDossier(@RequestParam("id") String id) {
        this.processAttachmentToDossierService.addDocumentToDossier(this.dossierService.getActiveDossier(), id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/remove-document")
    public ResponseEntity<Dossier> removeDocument(@RequestParam("id") String documentId) {
        this.processAttachmentToDossierService.removeDocumentFromDossier(this.dossierService.getActiveDossier(),
                documentId);
        return this.activeDossier();
    }

    @PostMapping("/process-invoice")
    public ResponseEntity<Void> processInvoiceForDossier(@RequestParam("id") String id) {
        this.processAttachmentToDossierService.processInvoiceForDossier(this.dossierService.getActiveDossier(), id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/remove-invoice")
    public ResponseEntity<Dossier> removeInvoice(@RequestParam("id") String invoiceId) {
        this.processAttachmentToDossierService.removeInvoice(this.dossierService.getActiveDossier(), invoiceId);
        return this.activeDossier();
    }

    @PostMapping("/remove-fee")
    public ResponseEntity<Dossier> removeFee(@RequestParam("feeId") String feeId) {
        this.processAttachmentToDossierService.removeFee(this.dossierService.getActiveDossier(), feeId);
        return this.activeDossier();
    }

    @PostMapping("/new-dossier")
    public ResponseEntity<Dossier> newDossier(@RequestBody Dossier dossier) {
        var saved = this.dossierService.newDossier(dossier);
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/update-dossier")
    public ResponseEntity<Dossier> updateActiveDossier(@RequestBody Dossier dossier) {
        var saved = this.dossierService.updateActiveDossier(dossier);
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/recall-for-modification")
    public ResponseEntity<Dossier> recallForModification(@RequestBody Dossier dossier) {
        var saved = this.dossierService.recallForModification(dossier);
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/close-active-dossier")
    public ResponseEntity<Dossier> closeActiveDossier() {
        var saved = this.dossierService.closeActiveDossier();
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/active-dossier")
    public ResponseEntity<Dossier> activeDossier() {
        return this.dossierService.getActiveDossier().map(ResponseEntity::ok).orElseGet(ResponseEntity.ok()::build);
    }

    @DeleteMapping("/active-dossier")
    public ResponseEntity<Map.Entry<String, String>> delete() {
        this.dossierService.delete();
        return ResponseEntity.ok(Map.entry("message", "dossier deleted"));
    }

    @PostConstruct
    public void initImportDossierExample() throws IOException {
        var rs = new ClassPathResource("dossier/import-dossier-example.xlsx");

        try (var is = rs.getInputStream()) {
            importDossierXlsxExample = RestUtil.transformToByteArrayResource("import.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml",
                    IOUtils.toByteArray(is));
        }

    }

}
