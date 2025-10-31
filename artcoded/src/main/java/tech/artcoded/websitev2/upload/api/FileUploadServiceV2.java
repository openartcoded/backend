package tech.artcoded.websitev2.upload.api;

import static java.util.Optional.ofNullable;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.openapitools.client.api.UploadRoutesApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.upload.FileUpload;
import tech.artcoded.websitev2.upload.FileUploadRdfService;
import tech.artcoded.websitev2.upload.FileUploadRepository;
import tech.artcoded.websitev2.upload.IFileUploadService;
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

  private final UploadRoutesApi uploadRoutesApi;

  public FileUploadServiceV2(FileUploadRepository fileUploadRepository, FileUploadRdfService fileUploadRdfService,
      MongoTemplate mongoTemplate, UploadRoutesApi uploadRoutesApi) {
    this.repository = fileUploadRepository;
    this.fileUploadRdfService = fileUploadRdfService;
    this.mongoTemplate = mongoTemplate;
    this.uploadRoutesApi = uploadRoutesApi;
  }

  @Override
  @SneakyThrows
  public File getFile(FileUpload fileUpload) {
    var response = uploadRoutesApi.downloadWithHttpInfo(fileUpload.getId());
    var file = response.getData();
    // handle case when file is inline
    if (!response.getHeaders().containsKey("content-disposition")
        && response.getHeaders().containsKey("content-type")) {
      var extension = FilenameUtils
          .getExtension(response.getHeaders().getOrDefault("content-type", List.of("")).getFirst());
      var renamed = new File(file.getAbsolutePath() + ofNullable(extension).map(e -> "." + e).orElse(""));
      if (file.renameTo(renamed)) {
        log.debug("filename now {}", renamed.getAbsolutePath());
        return renamed;
      }
    }
    return file;
  }

  @Override
  @SneakyThrows
  public String upload(FileUpload upload, InputStream is, boolean publish) {
    try (var stream = new BufferedInputStream(is)) {
      var bytes = IOUtils.toByteArray(stream);
      var path = Paths.get(tmpfsPath, upload.getOriginalFilename());
      Files.write(path, bytes);
      var uploadV2 = uploadRoutesApi.upload(path.toFile(), upload.getCorrelationId(),
          Optional.ofNullable(upload.getId()).orElseGet(IdGenerators::get), upload.isPublicResource(), false);
      if (Boolean.TRUE.equals(uploadV2.getPublicResource()) && publish) {
        fileUploadRdfService.publish(() -> this.findOneByIdPublic(upload.getId()));
      }

      return uploadV2.getId();
    }
  }

  @Override
  @SneakyThrows
  public void delete(FileUpload upload) {
    log.info("delete upload {}", upload.getOriginalFilename());
    fileUploadRdfService.delete(upload.getId());
    uploadRoutesApi.deleteById(upload.getId());
  }

}
