package tech.artcoded.websitev2.changelogs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.gridfs.model.GridFSFile;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.bson.Document;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import tech.artcoded.websitev2.api.helper.DateHelper;
import tech.artcoded.websitev2.upload.FileUpload;
import tech.artcoded.websitev2.upload.FileUploadService;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import static java.util.Optional.ofNullable;
import static org.apache.commons.io.IOUtils.toByteArray;
import static tech.artcoded.websitev2.api.func.CheckedSupplier.toSupplier;

@Slf4j
@ChangeUnit(id = "rewrite-file-upload",
  order = "24",
  author = "Nordine Bittich")
public class $24_RewriteFileUpload {

  @RollbackExecution
  public void rollbackExecution() {
  }

  @Execution
  public void execute(MongoDatabaseFactory mongoDatabaseFactory,
                      MappingMongoConverter mappingMongoConverter,
                      FileUploadService uploadService) throws IOException {

    var gridFsTemplate = new GridFsTemplate(mongoDatabaseFactory, mappingMongoConverter);
    var mapper = new ObjectMapper();
    var gridFsFiles = gridFsTemplate.find(new Query()).into(new ArrayList<>());
    for (GridFSFile gridFSFile : gridFsFiles) {
      var metadata = mapper.readValue(ofNullable(gridFSFile.getMetadata()).map(Document::toJson).orElse("{}"), FileUploadMetadata.class);
      var id = gridFSFile.getObjectId().toString();
      try (var is = uploadToInputStream(gridFsTemplate, gridFSFile)) {
        FileUpload upload = FileUpload.builder()
          .id(id)
          .correlationId(metadata.correlationId)
          .extension(FilenameUtils.getExtension(metadata.getOriginalFilename()))
          .creationDate(DateHelper.stringToDate(metadata.creationDate))
          .contentType(metadata.contentType)
          .name(metadata.name)
          .size(metadata.size)
          .originalFilename(metadata.originalFilename)
          .publicResource(metadata.publicResource)
          .build();
        uploadService.upload(upload, is, false);
        log.info("migrate {} with id {}", upload.getOriginalFilename(), upload.getId());
      }
    }
    gridFsTemplate.delete(new Query());
  }

  private byte[] uploadToByteArray(GridFsTemplate gridFsTemplate, GridFSFile upload) {
    return resourceToByteArray(uploadToResource(gridFsTemplate, upload));
  }

  private byte[] resourceToByteArray(GridFsResource upload) {
    return toSupplier(() -> toByteArray(this.resourceToInputStream(upload))).get();
  }

  private InputStream uploadToInputStream(GridFsTemplate gridFsTemplate, GridFSFile upload) {
    return resourceToInputStream(uploadToResource(gridFsTemplate, upload));
  }

  private InputStream resourceToInputStream(GridFsResource upload) {
    return toSupplier(upload::getInputStream).get();
  }

  private GridFsResource uploadToResource(GridFsTemplate gridFsTemplate, GridFSFile upload) {
    return gridFsTemplate.getResource(upload);
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  static class FileUploadMetadata {
    private String creationDate;
    private String contentType;
    private String originalFilename;
    private String correlationId;
    private String name;
    private long size;
    private boolean publicResource;

  }
}
