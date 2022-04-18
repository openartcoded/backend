package tech.artcoded.websitev2.upload;

import com.mongodb.client.gridfs.model.GridFSFile;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.apache.commons.compress.utils.FileNameUtils;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tech.artcoded.websitev2.rdf.SparqlQueryStore;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static tech.artcoded.websitev2.upload.FileUploadService.*;

@Service
@Slf4j
public class FileUploadRdfService {

  private final ProducerTemplate producerTemplate;

  @Value("${application.upload.defaultGraph}")
  private String defaultGraph;

  public final SparqlQueryStore sparqlQueryStore;

  public FileUploadRdfService(ProducerTemplate producerTemplate,
                              SparqlQueryStore sparqlQueryStore) {
    this.producerTemplate = producerTemplate;
    this.sparqlQueryStore = sparqlQueryStore;
  }

  @Async
  public void publish(GridFSFile upl) {
    pub(upl);
  }

  @Async
  public void publish(Supplier<Optional<GridFSFile>> uplSupplier) {
    uplSupplier.get().ifPresent(this::pub);
  }

  @Async
  public void delete(String id) {
    var computedQuery = sparqlQueryStore.getQueryWithParameters("deleteUploadRdf", Map.of(
      "graph", defaultGraph,
      "id", id
    ));
    this.producerTemplate.sendBody("jms:queue:sparql-update", ExchangePattern.InOnly, computedQuery);
  }

  private void pub(GridFSFile upl) {

    Document metadata = upl.getMetadata();
    var contentType = GET_METADATA.apply(metadata, GRID_FS_CONTENT_TYPE).orElse("");
    var originalFileName = GET_METADATA.apply(metadata, GRID_FS_ORIGINAL_FILE_NAME).orElse("");
    var uploadDate = upl.getUploadDate();
    long length = upl.getLength();
    String fileUploadId = upl.getObjectId().toString();
    var computedQuery = sparqlQueryStore.getQueryWithParameters("publicUploadRdf", Map.of(
      "graph", defaultGraph,
      "id", fileUploadId,
      "contentType", contentType,
      "originalFileName", originalFileName,
      "fileExtension", FileNameUtils.getExtension(upl.getFilename()),
      "uploadDate", uploadDate,
      "length", length
    ));
    this.producerTemplate.sendBody("jms:queue:sparql-update", ExchangePattern.InOnly, computedQuery);
  }

}
