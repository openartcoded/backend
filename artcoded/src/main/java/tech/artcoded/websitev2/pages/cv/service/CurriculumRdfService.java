package tech.artcoded.websitev2.pages.cv.service;

import java.util.Map;

import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.pages.cv.entity.Curriculum;
import tech.artcoded.websitev2.pages.cv.repository.CurriculumRepository;
import tech.artcoded.websitev2.template.OaTemplateRenderer;
import tech.artcoded.websitev2.template.OaTemplateType;

/**
 * Pushes the cv to the triplestore: the sparql update is the default CV_RDF template (jinja, rendered by the file
 * service; the bundled FreeMarker one when the file service can't render it).
 */
@Service
@Slf4j
public class CurriculumRdfService {

    private final ProducerTemplate producerTemplate;

    @Value("${application.cv.defaultGraph}")
    private String defaultGraph;

    private final OaTemplateRenderer templateRenderer;
    private final CurriculumRepository curriculumRepository;

    public CurriculumRdfService(ProducerTemplate producerTemplate, OaTemplateRenderer templateRenderer,
            CurriculumRepository curriculumRepository) {
        this.producerTemplate = producerTemplate;
        this.templateRenderer = templateRenderer;
        this.curriculumRepository = curriculumRepository;
    }

    @Async
    public void pushTriples(String curriculumId) {
        Curriculum cv = curriculumRepository.findById(curriculumId)
                .orElseThrow(() -> new RuntimeException("no cv found"));
        var computedQuery = sparqlUpdate(cv);
        this.producerTemplate.sendBody("jms:queue:sparql-update", ExchangePattern.InOnly, computedQuery);
    }

    String sparqlUpdate(Curriculum cv) {
        return templateRenderer.renderDefaultText(OaTemplateType.CV_RDF, Map.of("graph", defaultGraph, "cv", cv));
    }
}
