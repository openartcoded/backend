package tech.artcoded.websitev2.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notification")
@Slf4j
public class NotificationController {
  private final NotificationService notificationService;

  public NotificationController(NotificationService notificationService) {
    this.notificationService = notificationService;
  }

  @GetMapping
  public List<Notification> getLatestNotification() {
    return notificationService.latest();
  }

  @PostMapping
  public ResponseEntity<Void> update(
    @RequestParam("id") String id, @RequestParam("seen") boolean seen) {
    notificationService.update(id, seen);
    return ResponseEntity.ok().build();
  }

  @DeleteMapping
  public ResponseEntity<Void> delete(@RequestParam("id") String id) {
    return ResponseEntity.ok().build();
  }

}
