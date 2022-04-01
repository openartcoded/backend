package tech.artcoded.websitev2.rest.exception;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;

@ControllerAdvice
@Slf4j
public class DefaultExceptionHandler {

  public DefaultExceptionHandler() {
  }

  @ExceptionHandler({RuntimeException.class})
  public ResponseEntity<Map.Entry<String, String>> runtimeException(WebRequest webRequest, Exception exception) {
    log.error("an error occurred ", exception);
    return ResponseEntity.badRequest().body(Map.entry("stackTrace", ExceptionUtils.getStackTrace(exception)));
  }
}
