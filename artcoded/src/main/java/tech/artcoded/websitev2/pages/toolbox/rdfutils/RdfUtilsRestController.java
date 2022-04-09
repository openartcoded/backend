package tech.artcoded.websitev2.pages.toolbox.rdfutils;


import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import tech.artcoded.websitev2.rdf.ModelConverter;
import tech.artcoded.websitev2.rdf.ShaclValidationUtils;
import tech.artcoded.websitev2.rest.util.RestUtil;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/api/toolbox/public/rdf")
@Slf4j
public class RdfUtilsRestController {

  @PostMapping(value = "/file-to-lang",
               consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ByteArrayResource> fileToLang(
          @RequestPart("file") MultipartFile file, @RequestParam("lang") String lang) {
    try {
      String modelConverted =
              ModelConverter.inputStreamToLang(file.getOriginalFilename(), file::getInputStream, lang);
      return RestUtil.transformToByteArrayResource(
              "file_"
                      .concat(System.currentTimeMillis() + "")
                      .concat(".")
                      .concat(ModelConverter.getExtension(lang)),
              ModelConverter.getContentType(lang),
              modelConverted.getBytes());
    }
    catch (Exception e) {
      String stackTrace = ExceptionUtils.getStackTrace(e);
      return RestUtil.transformToByteArrayResource(
              "error_".concat(System.currentTimeMillis() + "").concat(".txt"),
              MediaType.TEXT_PLAIN_VALUE,
              stackTrace.getBytes(StandardCharsets.UTF_8));
    }
  }

  @PostMapping(value = "/shacl-validation",
               consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<String> fileToLang(
          @RequestPart("modelFile") MultipartFile modelFile,
          @RequestPart("shaclFile") MultipartFile shaclFile) {
    final long limit = 10 * 1024 * 1024;
    if (shaclFile.getSize() > limit || modelFile.getSize() > limit) {
      throw new MaxUploadSizeExceededException(limit);
    }
    try {
      var headers = new HttpHeaders();
      headers.add(org.apache.http.HttpHeaders.CONTENT_TYPE, ModelConverter.getContentType("JSONLD"));
      Optional<String> validate = ShaclValidationUtils.validate(
              modelFile.getInputStream(), ModelConverter.filenameToLang(modelFile.getOriginalFilename()),
              shaclFile.getInputStream(), ModelConverter.filenameToLang(shaclFile.getOriginalFilename())
      );
      return validate.map(ResponseEntity.badRequest()::body).orElseGet(ResponseEntity.ok().headers(headers)::build);
    }
    catch (Exception e) {
      return ResponseEntity.unprocessableEntity().body(e.getMessage());
    }
  }

  @PostMapping(value = "/model-to-lang",
               consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<String> modelConversion(
          @RequestParam("model") String model,
          @RequestParam("langOfModel") String langOfModel,
          @RequestParam("lang") String lang) {
    try {
      String modelConverted = ModelConverter.convertModel(model, langOfModel, lang);
      var headers = new HttpHeaders();
      headers.add(org.apache.http.HttpHeaders.CONTENT_TYPE, ModelConverter.getContentType(lang));
      return ResponseEntity.ok().headers(headers).body(modelConverted);
    }
    catch (Exception e) {
      return ResponseEntity.unprocessableEntity().body(e.getMessage());
    }
  }

  @GetMapping("/allowed-languages")
  public List<String> getAllowedLanguages() {
    return ModelConverter.getAllowedLanguages();
  }

  @GetMapping("/allowed-extensions")
  public List<String> getAllowedExtensions() {
    return ModelConverter.getAllowedLanguages().stream()
                         .map(ModelConverter::getExtension)
                         .collect(Collectors.toList());
  }
}
