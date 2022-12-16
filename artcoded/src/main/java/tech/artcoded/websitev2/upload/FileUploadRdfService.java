package tech.artcoded.websitev2.upload;

import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tech.artcoded.websitev2.rdf.SparqlQueryStore;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

@Service
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
  public void publish(FileUpload upl) {
    pub(upl);
  }

  @Async
  public void publish(Supplier<Optional<FileUpload>> uplSupplier) {
    uplSupplier.get().ifPresent(this::pub);
  }

  @Async
  public void delete(String id) {
    var computedQuery = sparqlQueryStore.getQueryWithParameters("deleteUploadRdf", Map.of(
        "graph", defaultGraph,
        "id", id));
    this.producerTemplate.sendBody("jms:queue:sparql-update", ExchangePattern.InOnly, computedQuery);
  }

  private void pub(FileUpload upl) {

    var computedQuery = sparqlQueryStore.getQueryWithParameters("publicUploadRdf", Map.of(
        "graph", defaultGraph,
        "id", upl.getId(),
        "contentType", upl.getContentType(),
        "originalFileName", upl.getOriginalFilename(),
        "fileExtension", upl.getExtension(),
        "uploadDate", upl.getCreationDateString(),
        "length", upl.getSize()));
    this.producerTemplate.sendBody("jms:queue:sparql-update", ExchangePattern.InOnly, computedQuery);
  }

}
