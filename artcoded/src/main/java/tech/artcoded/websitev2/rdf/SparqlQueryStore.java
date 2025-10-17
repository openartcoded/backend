package tech.artcoded.websitev2.rdf;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateMethodModelEx;
import lombok.SneakyThrows;
import tech.artcoded.websitev2.utils.helper.IdGenerators;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.ui.freemarker.FreeMarkerTemplateUtils.processTemplateIntoString;

public interface SparqlQueryStore {
    Configuration CFG = new Configuration(Configuration.VERSION_2_3_31);

    Map<String, String> asMap();

    default String getQuery(String queryName) {
        return asMap().get(queryName);
    }

    default long size() {
        return asMap().size();
    }

    default boolean isPresent(String queryName) {
        return asMap().containsKey(queryName);
    }

    @SneakyThrows
    default String getQueryWithParameters(String queryName, Map<String, Object> parameters) {
        String query = getQuery(queryName);
        return computeQueryWithParameters(query, parameters);
    }

    static String computeQuery(String query, Object... parameters) {
        return query.formatted(parameters);
    }

    @SneakyThrows
    static String computeQueryWithParameters(String query, Map<String, Object> parameters) {
        Template template = new Template("name", new StringReader(query), CFG);
        Map<String, Object> params = new HashMap<>(parameters);
        params.put("_uuid", (TemplateMethodModelEx) (_) -> IdGenerators.get());
        return processTemplateIntoString(template, params);
    }
}
