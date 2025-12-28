package tech.artcoded.websitev2.rest.exception;

import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.pages.mail.MailJobRepository;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static tech.artcoded.websitev2.security.oauth.Role.ADMIN;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@ControllerAdvice
@Slf4j
public class DefaultExceptionHandler {
    private final MailJobRepository mailJobRepository;

    @Value("${application.admin.email}")
    private String adminEmail;

    public DefaultExceptionHandler(MailJobRepository mailJobRepository) {
        this.mailJobRepository = mailJobRepository;
    }

    @ExceptionHandler({ Exception.class })
    public ResponseEntity<Map.Entry<String, String>> runtimeException(WebRequest webRequest, Exception exception) {
        if (exception instanceof NoResourceFoundException nfe) {
            log.warn("resource {} not found", nfe.getBody());
            return ResponseEntity.notFound().build();
        }
        if (ExceptionUtils.indexOfThrowable(exception, IOException.class) != -1) {
            log.debug("client disconnected, response already closed", exception);
            return null;
        }
        log.error("an error occurred ", exception);
        Thread.startVirtualThread(() -> {
            try {
                log.warn("attempt to send exception by email");
                mailJobRepository.sendDelayedMail(List.of(adminEmail), "Artcoded error",
                        "<p>%s</p>".formatted(ExceptionUtils.getStackTrace(exception)), false, List.of(),
                        LocalDateTime.now().plusHours(2));

            } catch (Exception e) {
                log.error("could not send email", e);
            }
        });
        if (webRequest.isUserInRole(ADMIN.getAuthority())) {
            return ResponseEntity.badRequest().body(Map.entry("stackTrace", ExceptionUtils.getStackTrace(exception)));
        } else {
            return ResponseEntity.badRequest().body(Map.entry("error", "Server error"));
        }
    }
}
