package tech.artcoded.websitev2.pages.document;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tech.artcoded.websitev2.rest.util.MockMultipartFile;

import java.util.List;

import static java.util.Optional.ofNullable;

@RestController
@RequestMapping("/api/administrative-document")
public class AdministrativeDocumenController {
  private final AdministrativeDocumentService service;

  public AdministrativeDocumenController(AdministrativeDocumentService service) {
    this.service = service;
  }

  @DeleteMapping
  public ResponseEntity<Void> delete(@RequestParam("id") String id) {
    this.service.delete(id);
    return ResponseEntity.accepted().build();
  }

  @PostMapping("/find-all")
  public List<AdministrativeDocument> findAll() {
    return service.findAll();
  }

  @PostMapping("/find-by-id")
  public ResponseEntity<AdministrativeDocument> findById(@RequestParam("id") String id) {
    return service
        .findById(id)
        .map(ResponseEntity::ok)
        .orElseGet(ResponseEntity.notFound()::build);
  }

  @PostMapping("/toggle-bookmarked")
  public ResponseEntity<AdministrativeDocument> toggleBookmarked(@RequestParam("id") String id) {
    return service.toggleBookmarked(id).map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @PostMapping("/find-by-ids")
  public ResponseEntity<List<AdministrativeDocument>> findByIds(@RequestParam(value = "id") List<String> ids) {
    return ResponseEntity.ok(service.findAll(ids));
  }

  @PostMapping("/search")
  public Page<AdministrativeDocument> search(@RequestBody AdministrativeDocumentSearchCriteria searchCriteria,
      Pageable pageable) {
    return service.search(searchCriteria, pageable);
  }

  @PostMapping(value = "/save", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Void> save(
      @RequestParam("title") String title,
      @RequestParam(value = "id", required = false) String id,
      @RequestParam(value = "description", required = false) String description,
      @RequestParam(value = "tags", required = false) List<String> tags,
      @RequestPart(value = "document", required = false) MultipartFile document) {
    service.save(id, title, description, tags, ofNullable(document).map(MockMultipartFile::copy).orElse(null));
    return ResponseEntity.accepted().build();

  }

}
