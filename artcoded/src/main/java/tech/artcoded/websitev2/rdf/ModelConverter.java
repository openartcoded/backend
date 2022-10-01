package tech.artcoded.websitev2.rdf;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.artcoded.websitev2.utils.func.CheckedSupplier;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;


public interface ModelConverter {

  Logger LOG = LoggerFactory.getLogger(ModelConverter.class);

  static String modelToLang(Model model, String lang) {
    if (model.isEmpty()) throw new RuntimeException("model cannot be empty");
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    model.write(out, lang);
    return out.toString();
  }

  static Model toModel(String value, String lang) {
    if (StringUtils.isEmpty(value)) throw new RuntimeException("model cannot be empty");
    return toModel(() -> IOUtils.toInputStream(value, StandardCharsets.UTF_8), lang);
  }

  static boolean equals(Model firstModel, Model secondModel) {
    return firstModel.isIsomorphicWith(secondModel);
  }

  static Model difference(Model firstModel, Model secondModel) {
    return firstModel.difference(secondModel);
  }

  static Model toModel(CheckedSupplier<InputStream> is, String lang) {
    try (var stream = is.safeGet()) {
      Model graph = ModelFactory.createDefaultModel();
      graph.read(stream, "", lang);
      return graph;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  static String convertModel(String model, String lang, String langToConvert) {
    Model graph = ModelFactory.createDefaultModel();
    graph.read(IOUtils.toInputStream(model, StandardCharsets.UTF_8), "", lang);
    return modelToLang(graph, langToConvert);
  }

  static String inputStreamToLang(String filename, CheckedSupplier<InputStream> file, String lang) {
    return modelToLang(inputStreamToModel(filename, file), lang);
  }

  static Model inputStreamToModel(String filename, CheckedSupplier<InputStream> file) {

    String rdfFormat = filenameToLang(filename, RDFLanguages.TURTLE).getName();
    return toModel(file, rdfFormat);
  }

  static boolean checkFileFormat(String filename) {
    try {
      return filenameToLang(filename)!=null;
    } catch (Exception e) {
      LOG.error("format not recognized", e);
    }
    return false;
  }

  static Lang filenameToLang(String filename) {
    return RDFLanguages.filenameToLang(filename);
  }

  static Lang filenameToLang(String filename, Lang fallback) {
    return RDFLanguages.filenameToLang(filename, fallback);
  }

  static String getContentType(String lang) {
    return getRdfLanguage(lang).getContentType().getContentTypeStr();
  }

  static String getExtension(String lang) {
    return getRdfLanguage(lang).getFileExtensions().stream().findFirst().orElse("txt");
  }

  static Lang getRdfLanguage(String lang) {
    return RDFLanguages.nameToLang(lang);
  }

  static List<String> getLanguages() {
    return RDFLanguages.getRegisteredLanguages()
      .stream().map(Lang::getName).collect(Collectors.toList());
  }

  static List<String> getAllowedLanguages() {
    return List.of(
      "RDF/XML",
      "N-Triples",
      "TriX",
      "JSON-LD",
      "RDF-THRIFT",
      "Turtle",
      "N3",
      "N-Quads",
      "RDF/JSON",
      "TriG");
  }

}
