package tech.artcoded.websitev2.upload;

import static org.apache.commons.io.FileUtils.copyToFile;
import static tech.artcoded.websitev2.utils.func.CheckedSupplier.toSupplier;

import java.io.File;
import java.io.InputStream;

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class LegacyFileUploadService implements IFileUploadService {
  @Getter
  private final FileUploadRepository repository;
  private final FileUploadRdfService fileUploadRdfService;
  @Getter
  private final MongoTemplate mongoTemplate;

  @Value("${application.upload.pathToUpload}")
  private String pathToUploads;

  public LegacyFileUploadService(FileUploadRepository fileUploadRepository, FileUploadRdfService fileUploadRdfService,
      MongoTemplate mongoTemplate) {
    this.repository = fileUploadRepository;
    this.fileUploadRdfService = fileUploadRdfService;
    this.mongoTemplate = mongoTemplate;
  }

  public File getFile(FileUpload fileUpload) {
    return new File(getUploadFolder(), getFileNameOnDisk(fileUpload));
  }

  @SneakyThrows
  public byte[] uploadToByteArray(FileUpload upload) {
    File file = this.getFile(upload);
    if (file.exists()) {
      return FileUtils.readFileToByteArray(file);
    } else {
      throw new RuntimeException("File doesn't exist");
    }
  }

  private String getFileNameOnDisk(FileUpload fileUpload) {
    return "%s.%s".formatted(fileUpload.getId(), fileUpload.getExtension());
  }

  @SneakyThrows
  public InputStream uploadToInputStream(FileUpload upload) {
    File file = this.getFile(upload);
    if (file.exists()) {
      return FileUtils.openInputStream(file);
    } else {
      throw new RuntimeException("File doesn't exist");
    }
  }

  @SneakyThrows
  public String upload(FileUpload upload, InputStream is, boolean publish) {
    File toStore = this.getFile(upload);
    copyToFile(is, toStore);
    repository.save(upload.toBuilder().size(toStore.length()).build());
    if (upload.isPublicResource() && publish) {
      fileUploadRdfService.publish(() -> this.findOneByIdPublic(upload.getId()));
    }
    return upload.getId();
  }

  public void delete(FileUpload upload) {
    log.info("delete upload {}", upload.getOriginalFilename());
    fileUploadRdfService.delete(upload.getId());
    repository.deleteById(upload.getId());
    toSupplier(() -> FileUtils.delete(this.getFile(upload))).get();
  }

  public File getUploadFolder() {
    File uploadFolder = new File(pathToUploads);
    if (!uploadFolder.exists()) {
      boolean result = uploadFolder.mkdirs();
      log.debug("creating upload folder: {}", result);
    }
    return uploadFolder;
  }

}
