package tech.artcoded.websitev2.rest.exception;

import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.utils.service.MailService;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Map;

@ControllerAdvice
@Slf4j
public class DefaultExceptionHandler {
  private final MailService mailService;

  @Value("${application.admin.email}")
  private String adminEmail;

  public DefaultExceptionHandler(MailService mailService) {
    this.mailService = mailService;
  }

  @ExceptionHandler({ Exception.class })
  public ResponseEntity<Map.Entry<String, String>> runtimeException(WebRequest webRequest, Exception exception) {
    if (exception instanceof NoResourceFoundException nfe) {
      log.warn("resource {} not found", nfe.getBody());
      return ResponseEntity.notFound().build();
    }
    log.error("an error occurred ", exception);
    Thread.startVirtualThread(() -> {
      try {
        log.warn("attempt to send exception by email");
        mailService.sendMail(List.of(adminEmail), "Artcoded error",
            "<p>%s</p>".formatted(ExceptionUtils.getStackTrace(exception)), false, List::of);

      } catch (Exception e) {
        log.error("could not send email", e);
      }
    });
    return ResponseEntity.badRequest().body(Map.entry("stackTrace", ExceptionUtils.getStackTrace(exception)));
  }
}
