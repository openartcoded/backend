package tech.artcoded.websitev2.pages.fee;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.inject.Inject;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/fee")
@Slf4j
public class FeeController {

  private final FeeService feeService;

  @Inject
  public FeeController(FeeService feeService) {
    this.feeService = feeService;
  }

  @DeleteMapping
  public ResponseEntity<Map.Entry<String, String>> delete(@RequestParam("id") String id) {
    log.warn("fee {} will be really deleted", id);
    this.feeService.delete(id);
    return ResponseEntity.ok(Map.entry("message", "fee deleted"));
  }

  @PostMapping("/find-all")
  public List<Fee> findAll() {
    return feeService.findAll();
  }

  @PostMapping("/find-by-id")
  public ResponseEntity<Fee> findById(@RequestParam("id") String id) {
    return feeService
        .findById(id)
        .map(ResponseEntity::ok)
        .orElseGet(ResponseEntity.notFound()::build);
  }

  @PostMapping("/find-by-ids")
  public ResponseEntity<List<Fee>> findByIds(@RequestParam(value = "id") List<String> ids) {
    return ResponseEntity.ok(feeService.findAll(ids));
  }

  @PostMapping("toggle-bookmarked")
  public ResponseEntity<Fee> toggleBookmarked(@RequestParam("id") String id) {
    return feeService.toggleBookmarked(id).map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @PostMapping("/search")
  public Page<Fee> findAll(@RequestBody FeeSearchCriteria searchCriteria, Pageable pageable) {
    return feeService.search(searchCriteria, pageable);
  }

  @PostMapping(value = "/manual-submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Fee manualSubmit(
      @RequestParam("subject") String subject,
      @RequestParam("body") String body,
      @RequestPart("files") MultipartFile[] files) {
    return feeService.save(subject, body, new Date(), Arrays.asList(files));
  }

  @PostMapping("/update-tag")
  public ResponseEntity<List<Fee>> updateTag(
      @RequestBody List<String> tagIds, @RequestParam("tag") String tag) {
    List<Fee> fees = this.feeService.updateTag(tag, tagIds);
    return ResponseEntity.ok(fees);
  }

  @PostMapping("/update-price")
  public ResponseEntity<Fee> updatePrice(@RequestParam("id") String id,
      @RequestParam("priceHVat") BigDecimal priceHVat,
      @RequestParam("vat") BigDecimal vat) {
    return feeService.updatePrice(id, priceHVat, vat).map(ResponseEntity::ok)
        .orElseGet(ResponseEntity.noContent()::build);
  }

  @PostMapping("/remove-attachment")
  public ResponseEntity<Fee> removeAttachment(
      @RequestParam("id") String feeId, @RequestParam("attachmentId") String attachmentId) {
    this.feeService.removeAttachment(feeId, attachmentId);
    return this.feeService
        .findById(feeId)
        .map(ResponseEntity::ok)
        .orElseGet(ResponseEntity.notFound()::build);
  }

  @PostMapping("/summaries")
  public List<FeeSummary> summaries() {
    return feeService.getSummaries();
  }
}
