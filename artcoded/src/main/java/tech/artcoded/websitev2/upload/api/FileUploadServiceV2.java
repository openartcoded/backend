package tech.artcoded.websitev2.upload.api;

import static java.util.Optional.ofNullable;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.openapitools.client.api.UploadRoutesApi;
import org.openapitools.client.model.DownloadBulkRequestUriParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.pages.mail.MailJobRepository;
import tech.artcoded.websitev2.upload.FileUpload;
import tech.artcoded.websitev2.upload.FileUploadRdfService;
import tech.artcoded.websitev2.upload.FileUploadRepository;
import tech.artcoded.websitev2.upload.IFileUploadService;
import tech.artcoded.websitev2.utils.func.CheckedSupplier;
import tech.artcoded.websitev2.utils.helper.IdGenerators;

@Service
@Primary
@Slf4j
public class FileUploadServiceV2 implements IFileUploadService {

  @Value("${application.tmpfs}")
  private String tmpfsPath;

  @Getter
  private final FileUploadRepository repository;
  private final FileUploadRdfService fileUploadRdfService;
  @Getter
  private final MongoTemplate mongoTemplate;
  private final MailJobRepository mailJobRepository;
  @Value("${application.admin.email}")
  private String adminEmail;
  private final UploadRoutesApi uploadRoutesApi;

  public FileUploadServiceV2(FileUploadRepository fileUploadRepository, FileUploadRdfService fileUploadRdfService,
      MongoTemplate mongoTemplate, UploadRoutesApi uploadRoutesApi, MailJobRepository mailJobRepository) {
    this.repository = fileUploadRepository;
    this.fileUploadRdfService = fileUploadRdfService;
    this.mongoTemplate = mongoTemplate;
    this.mailJobRepository = mailJobRepository;
    this.uploadRoutesApi = uploadRoutesApi;
  }

  @Override
  @SneakyThrows
  public File getFileById(String fileUploadId) {
    var response = uploadRoutesApi.downloadWithHttpInfo(fileUploadId);
    var file = response.getData();

    return _download(response.getHeaders(), file);
  }

  private File _download(Map<String, List<String>> headers, File file) {
    // handle case when file is inline
    if (!headers.containsKey("content-disposition") && headers.containsKey("content-type")) {
      var extension = getExtension(headers.getOrDefault("content-type", List.of("")).getFirst());
      var renamed = new File(file.getAbsolutePath() + extension);
      if (file.renameTo(renamed)) {
        log.debug("filename now {}", renamed.getAbsolutePath());
        return renamed;
      }
    }
    return file;
  }

  @SneakyThrows
  @Override
  public File downloadBulk(List<String> uploadIds) {
    var response = uploadRoutesApi.downloadBulkWithHttpInfo(new DownloadBulkRequestUriParams().ids(uploadIds));
    var file = response.getData();
    return _download(response.getHeaders(), file);
  }

  public static String getExtension(String contentType) {
    switch (contentType) {
      case "image/jpeg":
      case "image/jpg":
        return ".jpg";
      case "image/jxl":
        return ".jxl";
      case "image/png":
        return ".png";
      case "image/gif":
        return ".gif";
      case "image/bmp":
        return ".bmp";
      case "image/tiff":
        return ".tiff";
      case "image/webp":
        return ".webp";
      case "image/vnd.microsoft.icon":
        return ".ico";
      case "image/svg+xml":
        return ".svg";
      case "image/heif":
        return ".heif";
      case "image/heic":
        return ".heic";
      case "application/pdf":
        return ".pdf";
      case "text/html":
        return ".html";
      default:
        return "";
    }
  }

  @Override
  public File getFile(FileUpload fileUpload) {
    return getFileById(fileUpload.getId());
  }

  @SneakyThrows
  private File storeTemp(Path tmpDir, String filename, InputStream is) {
    tmpDir.toFile().mkdirs();
    var path = Paths.get(tmpDir.toString(), filename);

    try (var stream = new BufferedInputStream(is)) {
      FileUtils.copyInputStreamToFile(stream, path.toFile());
    }
    return path.toFile();
  }

  @Override
  @SneakyThrows
  public List<String> uploadAll(List<MultipartFile> uploads, String correlationId, boolean isPublic) {
    var tmpDir = Paths.get(tmpfsPath, IdGenerators.get());
    List<File> tempFiles = uploads.stream().map(u -> storeTemp(tmpDir, normalizeFilename(u.getOriginalFilename()),
        CheckedSupplier.toSupplier(() -> u.getInputStream()).get())).toList();
    var uploadsV2 = uploadRoutesApi.upload(tempFiles, correlationId, isPublic, false);
    cleanupTmpFolder(tmpDir);
    return uploadsV2.stream().map(u -> u.getId()).toList();
  }

  @Override
  @SneakyThrows
  public String upload(FileUpload upload, InputStream is, boolean publish) {
    var tmpDir = Paths.get(tmpfsPath, IdGenerators.get());
    var tempFile = storeTemp(tmpDir, upload.getOriginalFilename(), is);
    var uploadV2 = uploadRoutesApi.uploadUpdate(Optional.ofNullable(upload.getId()).orElseGet(IdGenerators::get),
        tempFile, upload.getCorrelationId(), upload.isPublicResource(), false);
    if (Boolean.TRUE.equals(uploadV2.getPublicResource()) && publish) {
      fileUploadRdfService.publish(() -> this.findOneByIdPublic(upload.getId()));
    }
    cleanupTmpFolder(tmpDir);
    return uploadV2.getId();
  }

  private void cleanupTmpFolder(Path tmpDir) {
    Thread.startVirtualThread(() -> {
      try {
        log.info("cleaning tmpfs dir in a minute...to delete: {}", tmpDir.toString());
        Thread.sleep(Duration.ofMinutes(1));
        FileUtils.deleteDirectory(tmpDir.toFile());
      } catch (Exception exc) {
        log.error("error while cleaning tmpfs directory", exc);
        mailJobRepository.sendDelayedMail(List.of(adminEmail), "file upload error",
            "<p>%s</p>".formatted(ExceptionUtils.getStackTrace(exc)), false, List.of(),
            LocalDateTime.now().plusMinutes(30));
      }
    });
  }

  @Override
  @SneakyThrows
  public void delete(FileUpload upload) {
    log.info("delete upload {}", upload.getOriginalFilename());
    fileUploadRdfService.delete(upload.getId());
    uploadRoutesApi.deleteById(upload.getId());
  }

  @Override
  public File getTempFolder() {
    // should never be used in this ctx. only for legacy and download bulk
    return new File(tmpfsPath);
  }

}
