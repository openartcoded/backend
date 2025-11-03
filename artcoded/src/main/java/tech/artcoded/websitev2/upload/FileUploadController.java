package tech.artcoded.websitev2.upload;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tech.artcoded.websitev2.rest.util.RestUtil;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static java.util.Optional.of;
import static java.util.Optional.ofNullable;

@RestController
@RequestMapping("/api/resource")
public class FileUploadController {
  private final IFileUploadService uploadService;
  private final CorrelationLinkService linkService;

  @Inject
  public FileUploadController(IFileUploadService uploadService, CorrelationLinkService linkService) {
    this.uploadService = uploadService;
    this.linkService = linkService;
  }

  @GetMapping("/find-by-id")
  public ResponseEntity<FileUpload> findById(@RequestParam("id") String id) {
    return uploadService.findOneById(id).map(ResponseEntity.ok()::body).orElseGet(ResponseEntity.notFound()::build);
  }

  @GetMapping("/public/find-by-id")
  public ResponseEntity<FileUpload> findByIdPublic(@RequestParam("id") String id) {
    return uploadService.findOneByIdPublic(id).map(ResponseEntity.ok()::body)
        .orElseGet(ResponseEntity.notFound()::build);
  }

  @GetMapping("/find-by-correlation-id")
  public List<FileUpload> findByCorrelationId(@RequestParam("correlationId") String correlationId) {
    return uploadService.findByCorrelationId(false, correlationId);
  }

  @GetMapping("/public/find-by-correlation-id")
  public List<FileUpload> findByCorrelationIdPublic(@RequestParam("correlationId") String correlationId) {
    return uploadService.findByCorrelationId(true, correlationId);
  }

  @GetMapping("/public/download/{id}")
  public ResponseEntity<ByteArrayResource> publicDownload(@PathVariable("id") String id) {
    return toDownload(() -> uploadService.findOneByIdPublic(id));
  }

  @GetMapping("/find-by-ids")
  public ResponseEntity<List<FileUpload>> findByIds(@RequestParam("id") List<String> ids,
      @RequestParam(value = "withThumb", required = false, defaultValue = "false") boolean withThumb) {
    var all = uploadService.findAll(ids, withThumb);
    return ResponseEntity.ok(all);
  }

  @PostMapping("/find-all")
  public ResponseEntity<Page<FileUpload>> findAll(@RequestBody FileUploadSearchCriteria criteria, Pageable pageable) {
    var all = uploadService.findAll(criteria, pageable);
    return ResponseEntity.ok(all);
  }

  @PostMapping("/toggle-bookmarked")
  public ResponseEntity<FileUpload> toggleBookmarked(@RequestParam("id") String id) {
    return uploadService.toggleBookmarked(id).map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @GetMapping("/download")
  public ResponseEntity<ByteArrayResource> download(@RequestParam("id") String id) {
    return toDownload(() -> uploadService.findOneById(id));
  }

  private ResponseEntity<ByteArrayResource> toDownload(Supplier<Optional<FileUpload>> upload) {
    return upload.get().stream()
        .map(f -> RestUtil.transformToByteArrayResource(f.getOriginalFilename(),
            ofNullable(f.getContentType()).orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE),
            uploadService.uploadToByteArray(f)))
        .findFirst().orElseGet(ResponseEntity.notFound()::build);
  }

  @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<FileUpload> upload(@RequestPart("file") MultipartFile file,
      @RequestParam(value = "correlationId", required = false) String correlationId,
      @RequestParam(value = "publicResource", defaultValue = "false") boolean publicResource) throws Exception {
    return of(uploadService.upload(file, correlationId, publicResource)).stream()
        .map(id -> publicResource ? this.findByIdPublic(id) : this.findById(id)).findFirst()
        .orElseGet(ResponseEntity.badRequest()::build);
  }

  @PostMapping(value = "/correlation-links")
  public Map<String, String> getCorrelationLinks() {
    return linkService.getLinks();
  }

  @DeleteMapping("/delete-by-id")
  public Map.Entry<String, String> delete(@RequestParam("id") String id) {
    uploadService.delete(id);
    return Map.entry("message", id + " file will be deleted");
  }

  @DeleteMapping("/delete-all")
  public Map.Entry<String, String> deleteAll() {
    Thread.startVirtualThread(uploadService::deleteAll);
    return Map.entry("message", "all files will be deleted");
  }
}
