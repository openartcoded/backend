package tech.artcoded.websitev2.pages.dossier;


import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tech.artcoded.websitev2.rest.annotation.SwaggerHeaderAuthentication;
import tech.artcoded.websitev2.rest.util.RestUtil;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static tech.artcoded.websitev2.api.func.CheckedSupplier.toSupplier;

@RestController
@RequestMapping("/api/dossier")
@Slf4j
public class DossierController {

  private final DossierService dossierService;
  private final XlsReportService xlsReportService;

  @Inject
  public DossierController(DossierService dossierService, XlsReportService xlsReportService) {
    this.dossierService = dossierService;
    this.xlsReportService = xlsReportService;
  }

  @PostMapping("/find-all")
  @SwaggerHeaderAuthentication
  public List<Dossier> findAll(@RequestParam(value = "closed",
                                             defaultValue = "false") boolean closed) {
    return dossierService.findAll(closed);
  }

  @PostMapping("/summary")
  @SwaggerHeaderAuthentication
  public DossierSummary getSummary(@RequestParam(value = "id") String id) {
    return dossierService.getSummary(id);
  }

  @PostMapping("/find-by-id")
  @SwaggerHeaderAuthentication
  public ResponseEntity<Dossier> findById(@RequestParam("id") String id) {
    return dossierService.findById(id)
                         .map(ResponseEntity::ok)
                         .orElseGet(ResponseEntity.noContent()::build);
  }

  @GetMapping("/generate-summary")
  @SwaggerHeaderAuthentication
  public ResponseEntity<ByteArrayResource> generateSummary(@RequestParam("id") String id) {
    Optional<MultipartFile> summary = xlsReportService.generate(id);
    return summary.map(s -> RestUtil.transformToByteArrayResource(s.getOriginalFilename(), s.getContentType(), toSupplier(s::getBytes).get()))
                  .orElseGet(() -> ResponseEntity.noContent().build());
  }

  @PostMapping("/find-by-fee-id")
  @SwaggerHeaderAuthentication
  public ResponseEntity<Dossier> findByFeeId(@RequestParam("id") String id) {
    return dossierService.findByFeeId(id)
                         .map(ResponseEntity::ok)
                         .orElseGet(ResponseEntity.noContent()::build);
  }

  @PostMapping("/process-fees")
  @SwaggerHeaderAuthentication
  public ResponseEntity<Void> processFeesForDossier(@RequestBody List<String> feeIds) {
    this.dossierService.processFeesForDossier(feeIds);
    return ResponseEntity.ok().build();
  }

  @PostMapping("/process-invoice")
  @SwaggerHeaderAuthentication
  public ResponseEntity<Void> processFeesForDossier(@RequestParam("id") String id) {
    this.dossierService.processInvoiceForDossier(id);
    return ResponseEntity.ok().build();
  }

  @PostMapping("/remove-invoice")
  @SwaggerHeaderAuthentication
  public ResponseEntity<Dossier> removeInvoice(@RequestParam("id") String invoiceId) {
    this.dossierService.removeInvoice(invoiceId);
    return this.activeDossier();
  }

  @PostMapping("/remove-fee")
  @SwaggerHeaderAuthentication
  public ResponseEntity<Dossier> removeFee(@RequestParam("feeId") String feeId) {
    this.dossierService.removeFee(feeId);
    return this.activeDossier();
  }

  @PostMapping("/new-dossier")
  @SwaggerHeaderAuthentication
  public ResponseEntity<Dossier> newDossier(@RequestBody Dossier dossier) {
    var saved = this.dossierService.newDossier(dossier);
    return ResponseEntity.ok(saved);
  }

  @PostMapping("/update-dossier")
  @SwaggerHeaderAuthentication
  public ResponseEntity<Dossier> updateDossier(@RequestBody Dossier dossier) {
    var saved = this.dossierService.updateDossier(dossier);
    return ResponseEntity.ok(saved);
  }

  @PostMapping("/recall-for-modification")
  @SwaggerHeaderAuthentication
  public ResponseEntity<Dossier> recallForModification(@RequestBody Dossier dossier) {
    var saved = this.dossierService.recallForModification(dossier);
    return ResponseEntity.ok(saved);
  }

  @PostMapping("/close-active-dossier")
  @SwaggerHeaderAuthentication
  public ResponseEntity<Dossier> closeActiveDossier() {
    var saved = this.dossierService.closeActiveDossier();
    return ResponseEntity.ok(saved);
  }

  @PostMapping("/active-dossier")
  @SwaggerHeaderAuthentication
  public ResponseEntity<Dossier> activeDossier() {
    return this.dossierService.getActiveDossier()
                              .map(ResponseEntity::ok)
                              .orElseGet(ResponseEntity.ok()::build);
  }

  @DeleteMapping("/active-dossier")
  @SwaggerHeaderAuthentication
  public ResponseEntity<Map.Entry<String, String>> delete() {
    this.dossierService.delete();
    return ResponseEntity.ok(Map.entry("message", "dossier deleted"));
  }


}