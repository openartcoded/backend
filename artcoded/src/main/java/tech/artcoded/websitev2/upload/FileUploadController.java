package tech.artcoded.websitev2.upload;

import com.mongodb.client.gridfs.model.GridFSFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tech.artcoded.websitev2.rest.annotation.SwaggerHeaderAuthentication;
import tech.artcoded.websitev2.rest.annotation.SwaggerHeaderAuthenticationPageable;
import tech.artcoded.websitev2.rest.trait.PingControllerTrait;
import tech.artcoded.websitev2.rest.util.RestUtil;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static tech.artcoded.websitev2.upload.FileUploadService.GET_METADATA;
import static tech.artcoded.websitev2.upload.FileUploadService.GRID_FS_CONTENT_TYPE;

@RestController
@RequestMapping("/api/resource")
@Slf4j
public class FileUploadController
  implements PingControllerTrait {
  private final FileUploadService uploadService;

  @Inject
  public FileUploadController(FileUploadService uploadService) {
    this.uploadService = uploadService;
  }

  @SwaggerHeaderAuthentication
  @GetMapping("/find-by-id")
  public ResponseEntity<FileUploadDto> findById(@RequestParam("id") String id) {
    return uploadService
      .findOneById(id)
      .map(uploadService::toFileUploadDto)
      .map(ResponseEntity.ok()::body)
      .orElseGet(ResponseEntity.notFound()::build);
  }

  @GetMapping("/public/find-by-id")
  public ResponseEntity<FileUploadDto> findByIdPublic(@RequestParam("id") String id) {
    return uploadService
      .findOneByIdPublic(id)
      .map(uploadService::toFileUploadDto)
      .map(ResponseEntity.ok()::body)
      .orElseGet(ResponseEntity.notFound()::build);
  }

  @SwaggerHeaderAuthentication
  @GetMapping("/find-by-correlation-id")
  public List<FileUploadDto> findByCorrelationId(@RequestParam("correlationId") String correlationId) {
    return uploadService.findByCorrelationId(false, correlationId).stream()
      .map(uploadService::toFileUploadDto)
      .collect(Collectors.toList());
  }

  @GetMapping("/public/find-by-correlation-id")
  public List<FileUploadDto> findByCorrelationIdPublic(@RequestParam("correlationId") String correlationId) {
    return uploadService.findByCorrelationId(true, correlationId).stream()
      .map(uploadService::toFileUploadDto)
      .collect(Collectors.toList());
  }

  @GetMapping("/public/download/{id}")
  public ResponseEntity<ByteArrayResource> publicDownload(@PathVariable("id") String id) {
    return toDownload(uploadService.findOneByIdPublic(id));
  }

  @SwaggerHeaderAuthentication
  @GetMapping("/find-by-ids")
  public ResponseEntity<List<FileUploadDto>> findByIds(@RequestParam("id") List<String> ids) {
    List<FileUploadDto> all = uploadService
      .findAll(ids);
    return ResponseEntity.ok(all);
  }

  @SwaggerHeaderAuthenticationPageable
  @PostMapping("/find-all")
  public ResponseEntity<Page<FileUploadDto>> findAll(@RequestBody FileUploadSearchCriteria criteria, Pageable pageable) {
    Page<FileUploadDto> all = uploadService
      .findAll(criteria, pageable);
    return ResponseEntity.ok(all);
  }

  @GetMapping("/download")
  @SwaggerHeaderAuthentication
  public ResponseEntity<ByteArrayResource> download(@RequestParam("id") String id) {
    return toDownload(uploadService.findOneById(id));
  }

  private ResponseEntity<ByteArrayResource> toDownload(Optional<GridFSFile> upload) {
    return upload.stream()
      .map(f -> RestUtil.transformToByteArrayResource(
        f.getFilename(), GET_METADATA.apply(f.getMetadata(), GRID_FS_CONTENT_TYPE)
          .orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE), uploadService.uploadToByteArray(f)))
      .findFirst()
      .orElseGet(ResponseEntity.notFound()::build);
  }

  @PostMapping(value = "/upload",
    consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @SwaggerHeaderAuthentication
  public ResponseEntity<FileUploadDto> upload(@RequestPart("file") MultipartFile file,
                                              @RequestParam(value = "correlationId",
                                                required = false) String correlationId,
                                              @RequestParam(value = "publicResource",
                                                defaultValue = "false") boolean publicResource
  ) throws Exception {
    return Optional.of(uploadService.upload(file, correlationId, publicResource)).stream()
      .map(id -> publicResource ? this.findByIdPublic(id):this.findById(id))
      .findFirst()
      .orElseGet(ResponseEntity.badRequest()::build);
  }

  @DeleteMapping("/delete-by-id")
  @SwaggerHeaderAuthentication
  public Map.Entry<String, String> delete(@RequestParam("id") String id) {
    GridFSFile byId =
      uploadService.findOneById(id).stream()
        .findFirst()
        .orElseThrow(() -> new RuntimeException("Upload not found on disk"));
    CompletableFuture.runAsync(() -> uploadService.delete(byId.getObjectId().toString()));
    return Map.entry("message", id + " file will be deleted");
  }

  @DeleteMapping("/delete-all")
  @SwaggerHeaderAuthentication
  public Map.Entry<String, String> deleteAll() {
    CompletableFuture.runAsync(uploadService::deleteAll);
    return Map.entry("message", "all files will be deleted");
  }
}
