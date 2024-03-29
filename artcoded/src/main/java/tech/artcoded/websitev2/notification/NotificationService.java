package tech.artcoded.websitev2.notification;

import lombok.extern.slf4j.Slf4j;
import org.apache.camel.ProducerTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import static tech.artcoded.websitev2.utils.common.Constants.*;

@Service
@Slf4j
public class NotificationService {

  private final NotificationRepository notificationRepository;
  private final ProducerTemplate producerTemplate;

  public NotificationService(NotificationRepository notificationRepository, ProducerTemplate producerTemplate) {
    this.notificationRepository = notificationRepository;
    this.producerTemplate = producerTemplate;
  }

  List<Notification> latest() {
    return notificationRepository.findTop100ByOrderByReceivedDateDesc();
  }

  void update(String id, boolean seen) {
    this.notificationRepository
      .findById(id)
      .map(n -> n.toBuilder().seen(seen).build())
      .ifPresent(notificationRepository::save);
  }

  Notification notify(String title, String type, String correlationId) {
    return this.notificationRepository.save(Notification.builder().title(title)
      .correlationId(correlationId)
      .type(type).build());
  }

  @Async
  public void sendEvent(String title, String type, String correlationId) {
    if (title==null || type==null || correlationId==null) {
      log.error("receiving event with null value(s): title '{}', type '{}', correlation id '{}'", title, type, correlationId);
    } else {
      this.producerTemplate.sendBodyAndHeaders(NOTIFICATION_ENDPOINT, null, Map.of(
        NOTIFICATION_HEADER_TITLE, title,
        NOTIFICATION_HEADER_TYPE, type,
        CORRELATION_ID, correlationId
      ));
    }

  }


  void delete(String id) {
    notificationRepository.deleteById(id);
  }

  public void deleteAll() {
    this.notificationRepository.deleteAll();
  }
}
