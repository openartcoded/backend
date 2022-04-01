package tech.artcoded.websitev2.pages.contact;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tech.artcoded.websitev2.api.helper.IdGenerators;
import tech.artcoded.websitev2.notification.NotificationService;
import tech.artcoded.websitev2.rest.annotation.SwaggerHeaderAuthentication;

import javax.inject.Inject;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/form-contact")
@Slf4j
public class FormContactController {
  private static final String NOTIFICATION_TYPE = "NEW_PROSPECT";

  private final FormContactRepository formContactRepository;
  private final NotificationService notificationService;

  @Inject
  public FormContactController(
          FormContactRepository formContactRepository, NotificationService notificationService) {
    this.formContactRepository = formContactRepository;
    this.notificationService = notificationService;
  }

  @PostMapping("/find-all")
  @SwaggerHeaderAuthentication
  public List<FormContact> findAll() {
    return formContactRepository.findByOrderByCreationDateDesc();
  }

  @PostMapping("/submit")
  @SwaggerHeaderAuthentication
  public ResponseEntity<Void> submit(@RequestBody FormContact formContact) {
    CompletableFuture.runAsync(
            () -> {
              FormContact contact =
                      formContactRepository.save(
                              formContact.toBuilder().id(IdGenerators.get()).creationDate(new Date()).build());
              notificationService.sendEvent(
                      "New Prospect (%s)".formatted(contact.getEmail()), NOTIFICATION_TYPE, contact.getId());
            });
    return ResponseEntity.ok().build();
  }

  @DeleteMapping
  @SwaggerHeaderAuthentication
  public ResponseEntity<Map.Entry<String, String>> delete(@RequestParam("id") String id) {
    this.formContactRepository.deleteById(id);
    return ResponseEntity.ok(Map.entry("message", "form contact deleted"));
  }
}
