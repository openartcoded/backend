package tech.artcoded.websitev2.rdf;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static com.github.jsonldjava.shaded.com.google.common.collect.Maps.immutableEntry;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.io.FilenameUtils.removeExtension;
import static org.apache.commons.text.CaseUtils.toCamelCase;
import static tech.artcoded.websitev2.api.func.CheckedFunction.toFunction;

@Configuration
@Slf4j
public class SparqlConfig {

  @Value("${sparql.queryStore.path:classpath*:sparql}/*.sparql")
  private Resource[] queries;

  @Bean
  public SparqlQueryStore sparqlQueryLoader() {
    log.info("Adding {} queries to the store", queries.length);

    var queriesMap = Arrays.stream(queries)
                           .map(toFunction(r -> immutableEntry(toCamelCase(removeExtension(r.getFilename()), false, '-'),
                                                               IOUtils.toString(r.getInputStream(), UTF_8))))
                           .peek(e -> log.info("query '{}' added to the store", e.getKey()))
                           .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    return () -> queriesMap;
  }
}
