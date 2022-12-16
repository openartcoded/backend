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
import org.apache.commons.lang3.RegExUtils;
import org.bson.Document;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import tech.artcoded.websitev2.upload.FileUpload;
import tech.artcoded.websitev2.upload.FileUploadService;
import tech.artcoded.websitev2.utils.helper.DateHelper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import static java.net.URLConnection.guessContentTypeFromName;
import static java.util.Optional.ofNullable;
import static org.apache.commons.io.FilenameUtils.normalize;
import static org.apache.commons.lang3.StringUtils.stripAccents;
import static tech.artcoded.websitev2.utils.func.CheckedSupplier.toSupplier;

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
                      MongoTemplate mongoTemplate,
                      FileUploadService uploadService) throws IOException {

    var gridFsTemplate = new GridFsTemplate(mongoDatabaseFactory, mappingMongoConverter);
    var mapper = new ObjectMapper();
    var gridFsFiles = gridFsTemplate.find(new Query()).into(new ArrayList<>());
    for (GridFSFile gridFSFile : gridFsFiles) {
      var metadata = mapper.readValue(ofNullable(gridFSFile.getMetadata()).map(Document::toJson).orElse("{}"), FileUploadMetadata.class);
      var id = gridFSFile.getObjectId().toString();
      try (var is = uploadToInputStream(gridFsTemplate, gridFSFile)) {
        String fileName =
          normalize(
            RegExUtils.replaceAll(
              stripAccents(metadata.getOriginalFilename()), "[^a-zA-Z0-9\\.\\-]", "_"));
        FileUpload upload = FileUpload.builder()
          .id(id)
          .correlationId(metadata.correlationId)
          .extension(FilenameUtils.getExtension(metadata.getOriginalFilename()))
          .creationDate(DateHelper.stringToDate(metadata.creationDate))
          .contentType(ofNullable(metadata.contentType).orElseGet(() -> guessContentTypeFromName(fileName)))
          .name(metadata.name)
          .size(metadata.size)
          .originalFilename(fileName)
          .publicResource(metadata.publicResource)
          .build();
        uploadService.upload(upload, is, false);
        log.info("migrate {} with id {}", fileName, upload.getId());
      }
    }
    gridFsTemplate.delete(new Query());
    mongoTemplate.dropCollection("fs.chunks");
    mongoTemplate.dropCollection("fs.files");
  }

  // private byte[] uploadToByteArray(GridFsTemplate gridFsTemplate, GridFSFile upload) {
  //   return resourceToByteArray(uploadToResource(gridFsTemplate, upload));
  // }

  // private byte[] resourceToByteArray(GridFsResource upload) {
  //   return toSupplier(() -> toByteArray(this.resourceToInputStream(upload))).get();
  // }

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
