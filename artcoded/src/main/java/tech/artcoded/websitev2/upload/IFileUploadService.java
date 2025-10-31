package tech.artcoded.websitev2.upload;

import java.io.File;
import java.io.InputStream;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import static org.apache.commons.io.FilenameUtils.getExtension;
import static org.apache.commons.io.FilenameUtils.normalize;
import static org.apache.commons.lang3.StringUtils.stripAccents;
import static java.net.URLConnection.guessContentTypeFromName;
import static java.util.Optional.ofNullable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;
import org.apache.commons.lang3.RegExUtils;
import org.apache.commons.lang3.StringUtils;
import java.util.stream.Collectors;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import tech.artcoded.websitev2.rest.util.MockMultipartFile;

public interface IFileUploadService {

  FileUploadRepository getRepository();

  MongoTemplate getMongoTemplate();

  File getFile(FileUpload fileUpload);

  byte[] uploadToByteArray(FileUpload upload);

  InputStream uploadToInputStream(FileUpload upload);

  String upload(FileUpload upload, InputStream is, boolean publish);

  void delete(FileUpload upload);

  default Optional<FileUpload> toggleBookmarked(String id) {
    return getRepository().findById(id).map(
        upl -> getRepository().save(upl.toBuilder().updatedDate(new Date()).bookmarked(!upl.isBookmarked())
            .bookmarkedDate(upl.isBookmarked() ? null : new Date()).build()));
  }

  default Set<String> findAllCorrelationIds() {
    Query query = new Query();
    query.fields().include("correlationId");

    List<FileUpload> docs = getMongoTemplate().find(query, FileUpload.class);

    return docs.stream().map(d -> d.getCorrelationId()).filter(StringUtils::isNotBlank).collect(Collectors.toSet());
  }

  default void deleteByCorrelationId(String correlationId) {
    this.getRepository().findByCorrelationId(correlationId).forEach(this::delete);
  }

  default List<FileUpload> findAll() {
    return getMongoTemplate().find(new Query(), FileUpload.class);
  }

  default List<FileUpload> findAll(FileUploadSearchCriteria searchCriteria) {
    Query query = buildQuery(searchCriteria);
    return getMongoTemplate().find(query, FileUpload.class);
  }

  default Page<FileUpload> findAll(FileUploadSearchCriteria searchCriteria, Pageable pageable) {
    Query query = buildQuery(searchCriteria);

    long count = getMongoTemplate().count(Query.of(query), FileUpload.class);

    List<FileUpload> into = getMongoTemplate().find(query.with(pageable), FileUpload.class);

    return PageableExecutionUtils.getPage(into, pageable, () -> count);
  }

  default List<FileUpload> findAll(Collection<String> ids) {
    return getMongoTemplate().find(Query.query(Criteria.where("id").in(ids)), FileUpload.class);
  }

  default Optional<byte[]> getUploadAsBytes(String id) {
    return findOneById(id).map(this::uploadToByteArray);
  }

  default Optional<FileUpload> findOneById(String id) {
    return getRepository().findById(id);
  }

  default Optional<FileUpload> findOneByIdPublic(String id) {
    return getRepository().findByIdAndPublicResourceTrue(id);
  }

  default List<FileUpload> findByCorrelationId(boolean publicResource, String correlationId) {
    return getRepository().findByCorrelationIdAndPublicResourceIs(correlationId, publicResource);
  }

  default MultipartFile toMockMultipartFile(FileUpload fileUpload) {
    byte[] f = this.uploadToByteArray(fileUpload);
    return MockMultipartFile.builder().contentType(fileUpload.getContentType())
        .originalFilename(fileUpload.getOriginalFilename()).name(fileUpload.getName()).bytes(f).build();
  }

  default String upload(MultipartFile file, String correlationId, boolean isPublic) {
    return upload(file, correlationId, new Date(), isPublic);
  }

  default String upload(MultipartFile file, String correlationId, Date date, boolean isPublic) {
    try {
      String filename = normalize(
          RegExUtils.replaceAll(stripAccents(file.getOriginalFilename()), "[^a-zA-Z0-9\\.\\-]", "_"));
      FileUpload apUpload = FileUpload.builder()
          .contentType(ofNullable(file.getContentType()).orElseGet(() -> guessContentTypeFromName(filename)))
          .originalFilename(filename).name(file.getName()).size(file.getSize()).creationDate(date)
          .publicResource(isPublic).correlationId(correlationId).extension(getExtension(filename)).build();

      return upload(apUpload, file.getInputStream(), true);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  default void delete(String id) {
    if (StringUtils.isNotEmpty(id)) {
      Thread.ofVirtual().start(() -> getRepository().findById(id).ifPresent(this::delete));
    }
  }

  default void deleteAll() {
    this.findAll().stream().map(FileUpload::getId).forEach(this::delete);
  }

  default Optional<String> updateVisibility(String id, String correlationId, boolean publicResource) {
    return this.findOneById(id).map(file -> {
      var multipart = toMockMultipartFile(file);
      this.delete(file.getId());
      return this.upload(multipart, correlationId, publicResource);
    });
  }

  static Query buildQuery(FileUploadSearchCriteria searchCriteria) {
    List<Criteria> criteriaList = new java.util.ArrayList<>();
    Criteria criteria = null;

    if (StringUtils.isNotEmpty(searchCriteria.getCorrelationId())) {
      criteriaList.add(Criteria.where("correlationId").is(searchCriteria.getCorrelationId()));
    }

    if (StringUtils.isNotEmpty(searchCriteria.getId())) {
      criteriaList.add(Criteria.where("id").is(searchCriteria.getId()));
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
    if (searchCriteria.getOriginalFilename() != null) {
      criteriaList.add(Criteria.where("originalFilename")
          .regex(".*%s.*".formatted(searchCriteria.getOriginalFilename()), "i"));
    }
    if (searchCriteria.isBookmarked()) {
      criteriaList.add(Criteria.where("bookmarked").is(true));
    }

    if (!criteriaList.isEmpty()) {
      criteria = new Criteria().andOperator(criteriaList.toArray(new Criteria[0]));
    }

    return criteria != null ? Query.query(criteria) : new Query();
  }
}
