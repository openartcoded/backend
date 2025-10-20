package tech.artcoded.websitev2.notification;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Date;
import java.util.List;

public interface NotificationRepository extends MongoRepository<Notification, String> {
    List<Notification> findTop100ByOrderByReceivedDateDesc();

    void deleteBySeenIsTrueAndReceivedDateBefore(Date date);

    long countBySeenIsTrueAndReceivedDateBefore(Date date);

    void deleteBySeenIsTrueAndTypeIs(String type);

    void deleteByTypeIs(String type);
}
