package tech.artcoded.websitev2.pages.cv.service;

import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tech.artcoded.websitev2.pages.cv.entity.Curriculum;
import tech.artcoded.websitev2.pages.cv.repository.CurriculumRepository;
import tech.artcoded.websitev2.rdf.SparqlQueryStore;

import java.util.Map;

@Service
public class CurriculumRdfService {

  private final ProducerTemplate producerTemplate;

  @Value("${application.cv.defaultGraph}")
  private String defaultGraph;

  private final SparqlQueryStore queryStore;
  private final CurriculumRepository curriculumRepository;

  public CurriculumRdfService(ProducerTemplate producerTemplate,
      SparqlQueryStore queryStore,
      CurriculumRepository curriculumRepository) {
    this.producerTemplate = producerTemplate;
    this.queryStore = queryStore;
    this.curriculumRepository = curriculumRepository;
  }

  @Async
  public void pushTriples(String curriculumId) {
    Curriculum cv = curriculumRepository.findById(curriculumId).orElseThrow(() -> new RuntimeException("no cv found"));
    var computedQuery = queryStore.getQueryWithParameters("cvRdf", Map.of(
        "graph", defaultGraph,
        "cv", cv));
    this.producerTemplate.sendBody("jms:queue:sparql-update", ExchangePattern.InOnly, computedQuery);
  }
}
