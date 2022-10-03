package tech.artcoded.websitev2.pages.dossier;


import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tech.artcoded.websitev2.rest.util.MockMultipartFile;
import tech.artcoded.websitev2.rest.util.RestUtil;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static tech.artcoded.websitev2.utils.func.CheckedSupplier.toSupplier;

@RestController
@RequestMapping("/api/dossier")
@Slf4j
public class DossierController {
  private final DossierService dossierService;
  private final XlsReportService xlsReportService;
  private final CreateDossierFromXlsxService createDossierFromXlsxService;

  @Inject
  public DossierController(DossierService dossierService, XlsReportService xlsReportService, CreateDossierFromXlsxService createDossierFromXlsxService) {
    this.dossierService = dossierService;
    this.xlsReportService = xlsReportService;
    this.createDossierFromXlsxService = createDossierFromXlsxService;
  }

  @PostMapping("/find-all")
  public List<Dossier> findAll(@RequestParam(value = "closed",
    defaultValue = "false") boolean closed) {
    return dossierService.findAll(closed);
  }

  @PostMapping("/summary")
  public DossierSummary getSummary(@RequestParam(value = "id") String id) {
    return dossierService.getSummary(id);
  }

  @PostMapping("/find-by-id")
  public ResponseEntity<Dossier> findById(@RequestParam("id") String id) {
    return dossierService.findById(id)
      .map(ResponseEntity::ok)
      .orElseGet(ResponseEntity.noContent()::build);
  }

  @GetMapping("/generate-summary")
  public ResponseEntity<ByteArrayResource> generateSummary(@RequestParam("id") String id) {
    Optional<MultipartFile> summary = xlsReportService.generate(id);
    return summary.map(s -> RestUtil.transformToByteArrayResource(s.getOriginalFilename(), s.getContentType(), toSupplier(s::getBytes).get()))
      .orElseGet(() -> ResponseEntity.noContent().build());
  }

  @PostMapping("/find-by-fee-id")
  public ResponseEntity<Dossier> findByFeeId(@RequestParam("id") String id) {
    return dossierService.findByFeeId(id)
      .map(ResponseEntity::ok)
      .orElseGet(ResponseEntity.noContent()::build);
  }

  @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public void importDossierFromZip(
    @RequestPart(value = "zip") MultipartFile zip) {
    createDossierFromXlsxService.create(MockMultipartFile.copy(zip));
  }

  @PostMapping("/process-fees")
  public ResponseEntity<Void> processFeesForDossier(@RequestBody List<String> feeIds) {
    this.dossierService.processFeesForDossier(feeIds);
    return ResponseEntity.ok().build();
  }

  @PostMapping("/process-invoice")
  public ResponseEntity<Void> processInvoiceForDossier(@RequestParam("id") String id) {
    this.dossierService.processInvoiceForDossier(id);
    return ResponseEntity.ok().build();
  }

  @PostMapping("/remove-invoice")
  public ResponseEntity<Dossier> removeInvoice(@RequestParam("id") String invoiceId) {
    this.dossierService.removeInvoice(invoiceId);
    return this.activeDossier();
  }

  @PostMapping("/remove-fee")
  public ResponseEntity<Dossier> removeFee(@RequestParam("feeId") String feeId) {
    this.dossierService.removeFee(feeId);
    return this.activeDossier();
  }

  @PostMapping("/new-dossier")
  public ResponseEntity<Dossier> newDossier(@RequestBody Dossier dossier) {
    var saved = this.dossierService.newDossier(dossier);
    return ResponseEntity.ok(saved);
  }

  @PostMapping("/update-dossier")
  public ResponseEntity<Dossier> updateDossier(@RequestBody Dossier dossier) {
    var saved = this.dossierService.updateDossier(dossier);
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
    return this.dossierService.getActiveDossier()
      .map(ResponseEntity::ok)
      .orElseGet(ResponseEntity.ok()::build);
  }

  @DeleteMapping("/active-dossier")
  public ResponseEntity<Map.Entry<String, String>> delete() {
    this.dossierService.delete();
    return ResponseEntity.ok(Map.entry("message", "dossier deleted"));
  }


}
