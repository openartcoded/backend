package tech.artcoded.websitev2.upload;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RegExUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tech.artcoded.websitev2.rest.util.MockMultipartFile;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static java.net.URLConnection.guessContentTypeFromName;
import static java.util.Optional.ofNullable;
import static org.apache.commons.io.FileUtils.copyToFile;
import static org.apache.commons.io.FilenameUtils.getExtension;
import static org.apache.commons.io.FilenameUtils.normalize;
import static org.apache.commons.lang3.StringUtils.stripAccents;
import static tech.artcoded.websitev2.utils.func.CheckedSupplier.toSupplier;

@Service
@Slf4j
public class FileUploadService {
  private final FileUploadRepository fileUploadRepository;
  private final FileUploadRdfService fileUploadRdfService;
  private final MongoTemplate mongoTemplate;

  @Value("${application.upload.pathToUpload}")
  private String pathToUploads;

  public FileUploadService(FileUploadRepository fileUploadRepository, FileUploadRdfService fileUploadRdfService,
      MongoTemplate mongoTemplate) {
    this.fileUploadRepository = fileUploadRepository;
    this.fileUploadRdfService = fileUploadRdfService;
    this.mongoTemplate = mongoTemplate;
  }

  public List<FileUpload> findAll(FileUploadSearchCriteria searchCriteria) {
    Query query = buildQuery(searchCriteria);
    return mongoTemplate.find(query, FileUpload.class);
  }

  public Page<FileUpload> findAll(FileUploadSearchCriteria searchCriteria, Pageable pageable) {
    Query query = buildQuery(searchCriteria);

    long count = mongoTemplate.count(Query.of(query), FileUpload.class);

    List<FileUpload> into = mongoTemplate.find(query.with(pageable), FileUpload.class);

    return PageableExecutionUtils.getPage(into, pageable, () -> count);
  }

  public List<FileUpload> findAll(List<String> ids) {
    return mongoTemplate
        .find(Query.query(Criteria.where("id").in(ids)), FileUpload.class);
  }

  List<FileUpload> findAll() {
    return mongoTemplate.find(new Query(), FileUpload.class);
  }

  public Optional<byte[]> getUploadAsBytes(String id) {
    return findOneById(id).map(this::uploadToByteArray);
  }

  @SneakyThrows
  public byte[] uploadToByteArray(FileUpload upload) {
    File file = new File(getUploadFolder(), getFileNameOnDisk(upload));
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
    File file = new File(getUploadFolder(), getFileNameOnDisk(upload));
    if (file.exists()) {
      return FileUtils.openInputStream(file);
    } else {
      throw new RuntimeException("File doesn't exist");
    }
  }

  public Optional<FileUpload> findOneById(String id) {
    return fileUploadRepository.findById(id);
  }

  public Optional<FileUpload> findOneByIdPublic(String id) {
    return fileUploadRepository.findByIdAndPublicResourceTrue(id);
  }

  public List<FileUpload> findByCorrelationId(boolean publicResource, String correlationId) {
    return fileUploadRepository.findByCorrelationIdAndPublicResourceIs(correlationId, publicResource);
  }

  public MultipartFile toMockMultipartFile(FileUpload fileUpload) {
    byte[] f = this.uploadToByteArray(fileUpload);
    return MockMultipartFile.builder()
        .contentType(fileUpload.getContentType())
        .originalFilename(fileUpload.getOriginalFilename())
        .name(fileUpload.getName())
        .bytes(f)
        .build();
  }

  @SneakyThrows
  public String upload(FileUpload upload, InputStream is, boolean publish) {
    File toStore = new File(getUploadFolder(), getFileNameOnDisk(upload));
    copyToFile(is, toStore);
    fileUploadRepository.save(upload);
    if (upload.isPublicResource() && publish) {
      fileUploadRdfService.publish(() -> this.findOneByIdPublic(upload.getId()));
    }
    return upload.getId();
  }

  @SneakyThrows
  public String upload(MultipartFile file, String correlationId, boolean isPublic) {
    return upload(file, correlationId, new Date(), isPublic);
  }

  @SneakyThrows
  public String upload(MultipartFile file, String correlationId, Date date, boolean isPublic) {
    String filename = normalize(
        RegExUtils.replaceAll(
            stripAccents(file.getOriginalFilename()), "[^a-zA-Z0-9\\.\\-]", "_"));
    FileUpload apUpload = FileUpload.builder()
        .contentType(ofNullable(file.getContentType()).orElseGet(() -> guessContentTypeFromName(filename)))
        .originalFilename(filename)
        .name(file.getName())
        .size(file.getSize())
        .creationDate(date)
        .publicResource(isPublic)
        .correlationId(correlationId)
        .extension(getExtension(filename))
        .build();

    return upload(apUpload, file.getInputStream(), true);
  }

  @Async
  public void delete(String id) {
    if (StringUtils.isNotEmpty(id)) {
      fileUploadRepository.findById(id)
          .ifPresent(this::delete);
    }
  }

  public void delete(FileUpload upload) {
    log.info("delete upload {}", upload.getOriginalFilename());
    fileUploadRdfService.delete(upload.getId());
    fileUploadRepository.deleteById(upload.getId());
    toSupplier(() -> FileUtils.delete(new File(getUploadFolder(), getFileNameOnDisk(upload)))).get();
  }

  public void deleteAll() {
    this.findAll()
        .stream()
        .map(FileUpload::getId)
        .forEach(this::delete);
  }

  public Optional<String> updateVisibility(String id, String correlationId, boolean publicResource) {
    return this.findOneById(id)
        .map(file -> {
          var multipart = toMockMultipartFile(file);
          this.delete(file.getId());
          return this.upload(multipart, correlationId, publicResource);
        });
  }

  public void deleteByCorrelationId(String correlationId) {
    this.fileUploadRepository.findByCorrelationId(correlationId)
        .forEach(this::delete);
  }

  public File getUploadFolder() {
    File uploadFolder = new File(pathToUploads);
    if (!uploadFolder.exists()) {
      boolean result = uploadFolder.mkdirs();
      log.debug("creating upload folder: {}", result);
    }
    return uploadFolder;
  }

  private Query buildQuery(FileUploadSearchCriteria searchCriteria) {
    List<Criteria> criteriaList = new ArrayList<>();
    Criteria criteria = null;

    if (StringUtils.isNotEmpty(searchCriteria.getCorrelationId())) {
      criteriaList.add(Criteria.where("correlationId").is(searchCriteria.getCorrelationId()));
    }

    if (searchCriteria.getDateBefore() != null) {
      criteriaList.add(Criteria.where("creationDate").lt(searchCriteria.getDateBefore()));
    }

    if (searchCriteria.getDateAfter() != null) {
      criteriaList.add(Criteria.where("creationDate").gt(searchCriteria.getDateAfter()));
    }

    if (searchCriteria.getPublicResource() != null) {
      criteriaList.add(Criteria.where("publicResource").is(searchCriteria.getPublicResource()));
    }

    if (!criteriaList.isEmpty()) {
      criteria = new Criteria().andOperator(criteriaList.toArray(new Criteria[0]));
    }

    return criteria != null ? Query.query(criteria) : new Query();
  }
}
