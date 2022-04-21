package tech.artcoded.websitev2.pages.contact;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.artcoded.websitev2.api.helper.IdGenerators;
import tech.artcoded.websitev2.notification.NotificationService;

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
  public List<FormContact> findAll() {
    return formContactRepository.findByOrderByCreationDateDesc();
  }

  @PostMapping("/submit")
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
  public ResponseEntity<Map.Entry<String, String>> delete(@RequestParam("id") String id) {
    this.formContactRepository.deleteById(id);
    return ResponseEntity.ok(Map.entry("message", "form contact deleted"));
  }
}
