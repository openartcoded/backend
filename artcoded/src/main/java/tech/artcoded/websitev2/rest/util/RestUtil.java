package tech.artcoded.websitev2.rest.util;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Function;

public interface RestUtil {
  Logger LOGGER = LoggerFactory.getLogger(RestUtil.class);
  Function<MultipartFile, String> FILE_TO_JSON =
          file ->
                  Optional.ofNullable(file)
                          .map(
                                  f -> {
                                    try (var is = f.getInputStream()) {
                                      return IOUtils.toString(is, StandardCharsets.UTF_8);
                                    }
                                    catch (Exception e) {
                                      LOGGER.info("error transforming file", e);
                                      return null;
                                    }
                                  })
                          .orElse("{}");


  static ResponseEntity<ByteArrayResource> transformToByteArrayResource(
          String filename, String contentType, byte[] file) {
    return Optional.ofNullable(file)
                   .map(
                           u ->
                                   ResponseEntity.ok()
                                                 .header(HttpHeaders.CONTENT_TYPE, contentType)
                                                 .header(
                                                         HttpHeaders.CONTENT_DISPOSITION,
                                                         "attachment; filename=\"" + filename + "\"")
                                                 .body(new ByteArrayResource(file)))
                   .orElse(ResponseEntity.badRequest().body(null));
  }

  static String getClientIP(HttpServletRequest request) {
    String xfHeader = request.getHeader("X-Forwarded-For");
    if (xfHeader == null) {
      return request.getRemoteAddr();
    }
    return xfHeader.split(",")[0];
  }
}
