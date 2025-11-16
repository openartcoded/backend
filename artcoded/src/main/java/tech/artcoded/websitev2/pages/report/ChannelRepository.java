
package tech.artcoded.websitev2.pages.report;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface ChannelRepository extends MongoRepository<Channel, String> {

    Optional<Channel> findByCorrelationId(String correlationId);

    List<Channel> findByCreationDateAfter(Date date);

    List<Channel> findByCreationDateBefore(Date date);

    List<Channel> findBySubscribersContaining(String email);

    List<Channel> findByMessagesEmailFrom(String emailFrom);

    List<Channel> findByMessagesContentContainingIgnoreCase(String keyword);

    List<Channel> findByMessagesAttachmentIds(String attachmentId);

    long countBySubscribersContaining(String email);

    void deleteByCreationDateBefore(Date date);
}
