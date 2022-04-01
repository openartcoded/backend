package tech.artcoded.websitev2.upload;

import com.mongodb.client.gridfs.GridFSFindIterable;
import com.mongodb.client.gridfs.model.GridFSFile;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RegExUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tech.artcoded.websitev2.rest.util.MockMultipartFile;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static java.util.Optional.ofNullable;
import static org.apache.commons.io.FilenameUtils.normalize;
import static org.apache.commons.io.IOUtils.toByteArray;
import static org.apache.commons.lang3.StringUtils.stripAccents;
import static tech.artcoded.websitev2.api.func.CheckedSupplier.toSupplier;

@Service
@Slf4j
public class FileUploadService {

  public static String GRID_FS_CONTENT_TYPE = "contentType";
  public static String GRID_FS_SIZE = "size";
  public static String GRID_FS_ORIGINAL_FILE_NAME = "originalFilename";
  public static String GRID_FS_NAME = "name";
  public static BiFunction<Document, String, Optional<String>> GET_METADATA = (meta, k) -> ofNullable(meta).map(d -> d.getString(k));

  private final GridFsTemplate gridFsTemplate;
  private final MongoTemplate mongoTemplate;
  private final FileUploadRdfService rdfService;

  @Inject
  public FileUploadService(
          GridFsTemplate gridFsTemplate,
          MongoTemplate mongoTemplate, FileUploadRdfService rdfService) {
    this.gridFsTemplate = gridFsTemplate;
    this.mongoTemplate = mongoTemplate;
    this.rdfService = rdfService;
  }

  public List<GridFSFile> findAll(FileUploadSearchCriteria searchCriteria) {
    Query query = buildQuery(searchCriteria);
    List<GridFSFile> fileList = new ArrayList<>();
    return gridFsTemplate.find(query).into(fileList);
  }

  private Query buildQuery(FileUploadSearchCriteria searchCriteria) {
    List<Criteria> criteriaList = new ArrayList<>();
    Criteria criteria = null;

    if (StringUtils.isNotEmpty(searchCriteria.getCorrelationId())) {
      criteriaList.add(Criteria.where("metadata.correlationId").is(searchCriteria.getCorrelationId()));
    }

    if (searchCriteria.getDateBefore() != null) {
      criteriaList.add(Criteria.where("uploadDate").lt(searchCriteria.getDateBefore()));
    }

    if (searchCriteria.getDateAfter() != null) {
      criteriaList.add(Criteria.where("uploadDate").gt(searchCriteria.getDateAfter()));
    }

    if (searchCriteria.getPublicResource() != null) {
      criteriaList.add(Criteria.where("metadata.publicResource").is(searchCriteria.getPublicResource()));
    }

    if (!criteriaList.isEmpty()) {
      criteria = new Criteria().andOperator(criteriaList.toArray(new Criteria[0]));
    }

    return criteria != null ? Query.query(criteria) : new Query();
  }

  public Page<FileUploadDto> findAll(FileUploadSearchCriteria searchCriteria, Pageable pageable) {
    Query query = buildQuery(searchCriteria);

    List<FileUploadDto> fileList = new ArrayList<>();

    long count = mongoTemplate.count(Query.of(query), "fs.files");

    List<FileUploadDto> into =
            gridFsTemplate.find(query.with(pageable))
                          .map(this::toFileUploadDto)
                          .into(fileList);

    return PageableExecutionUtils.getPage(into, pageable, () -> count);
  }

  public List<FileUploadDto> findAll(List<String> ids) {
    List<FileUploadDto> fileList = new ArrayList<>();
    return gridFsTemplate
            .find(Query.query(Criteria.where("_id").in(ids)))
            .map(this::toFileUploadDto)
            .into(fileList);
  }

  @Nonnull
  public FileUploadDto toFileUploadDto(GridFSFile gridFSFile) {
    return FileUploadDto.builder()
                        .id(gridFSFile.getObjectId().toString())
                        .metadata(ofNullable(gridFSFile.getMetadata()).map(Document::toJson).orElse("{}"))
                        .build();
  }

  public byte[] uploadToByteArray(GridFSFile upload) {
    return resourceToByteArray(uploadToResource(upload));
  }

  public byte[] resourceToByteArray(GridFsResource upload) {
    return toSupplier(() -> toByteArray(this.resourceToInputStream(upload))).get();
  }

  public InputStream uploadToInputStream(GridFSFile upload) {
    return resourceToInputStream(uploadToResource(upload));
  }

  public InputStream resourceToInputStream(GridFsResource upload) {
    return toSupplier(upload::getInputStream).get();
  }

  public GridFsResource uploadToResource(GridFSFile upload) {
    return gridFsTemplate.getResource(upload);
  }

  public Optional<GridFSFile> findOneById(String id) {
    GridFSFile file = gridFsTemplate.findOne(new Query(Criteria.where("_id").is(id)));
    return ofNullable(file);
  }

  public Optional<GridFSFile> findOneByIdPublic(String id) {
    GridFSFile file =
            gridFsTemplate.findOne(
                    new Query(Criteria.where("_id").is(id).and("metadata.publicResource").is(true)));
    return ofNullable(file);
  }

  public List<GridFSFile> findByCorrelationId(boolean publicResource, String correlationId) {
    GridFSFindIterable files =
            gridFsTemplate.find(
                    new Query(
                            Criteria.where("metadata.correlationId")
                                    .is(correlationId)
                                    .and("metadata.publicResource")
                                    .is(publicResource)));
    return StreamSupport.stream(files.spliterator(), false).collect(Collectors.toList());
  }


  public MultipartFile toMockMultipartFile(GridFSFile file) {
    GridFsResource gfs = this.uploadToResource(file);
    byte[] f = this.resourceToByteArray(gfs);
    Document metadata = file.getMetadata();

    return MockMultipartFile.builder()
                            .contentType(GET_METADATA.apply(metadata, GRID_FS_CONTENT_TYPE).orElse(null))
                            .originalFilename(GET_METADATA.apply(metadata, GRID_FS_ORIGINAL_FILE_NAME).orElse(null))
                            .name(GET_METADATA.apply(metadata, GRID_FS_NAME).orElse(null))
                            .bytes(f)
                            .build();
  }

  public String upload(MultipartFile file, String correlationId, boolean isPublic) {
    FileUploadMetadata apUpload = FileUploadMetadata.newUpload(file, correlationId, isPublic);
    String filename =
            normalize(
                    RegExUtils.replaceAll(
                            stripAccents(file.getOriginalFilename()), "[^a-zA-Z0-9\\.\\-]", "_"));

    String id = gridFsTemplate
            .store(toSupplier(file::getInputStream).get(), filename, file.getContentType(), apUpload)
            .toString();

    if (isPublic) {
      rdfService.publish(() -> this.findOneByIdPublic(id));
    }

    return id;
  }

  public void delete(String id) {
    if (StringUtils.isNotEmpty(id)) {
      rdfService.delete(id);
      gridFsTemplate.delete(new Query(Criteria.where("_id").is(id)));
    }
  }

  public void deleteAll() {
    this.findAll(new FileUploadSearchCriteria())
        .stream()
        .map(gridFSFile -> gridFSFile.getObjectId().toString())
        .forEach(rdfService::delete);
    gridFsTemplate.delete(new Query());
  }

  public Optional<String> updateVisibility(String id, String correlationId, boolean publicResource) {
    return this.findOneById(id)
               .map(file -> {
                 var multipart = toMockMultipartFile(file);
                 this.delete(file.getObjectId().toString());
                 return this.upload(multipart, correlationId, publicResource);
               });
  }

  public void deleteByCorrelationId(String correlationId) {
    Query query = new Query(Criteria.where("metadata.correlationId").is(correlationId));
    gridFsTemplate.find(query)
                  .map(gridFSFile -> gridFSFile.getObjectId().toString())
                  .forEach(rdfService::delete);
    gridFsTemplate.delete(query);
  }
}
