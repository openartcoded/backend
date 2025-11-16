package tech.artcoded.websitev2.pages.report;

import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ChannelService {

  private final ChannelRepository channelRepository;
  private final MongoTemplate mongoTemplate;

  public Channel createChannel(String correlationId) {
    return channelRepository.save(Channel.builder().correlationId(correlationId).build());
  }

  public Optional<Channel> getChannel(String id) {
    return channelRepository.findById(id);
  }

  public Optional<Channel> getChannelByCorrelationId(String id) {
    return channelRepository.findByCorrelationId(id);
  }

  public Optional<Channel> updateChannel(Channel updatedChannel) {
    return channelRepository.findById(updatedChannel.getId())
        .map(existing -> existing.toBuilder().updatedDate(new Date())
            .messages(updatedChannel.getMessages())
            .subscribers(updatedChannel.getSubscribers()).build())
        .map(channelRepository::save);

  }

  public void addMessage(String channelId, Channel.Message message) {
    Query query = new Query(Criteria.where("_id").is(channelId));
    Update update = new Update().push("messages", message)
        .set("updatedDate", new Date());
    mongoTemplate.updateFirst(query, update, Channel.class);
  }

  public void deleteMessage(String channelId, String messageId) {
    Query query = new Query(Criteria.where("_id").is(channelId));
    Update update = new Update().pull("messages", Query.query(Criteria.where("id").is(messageId)))
        .set("updatedDate", new Date());
    mongoTemplate.updateFirst(query, update, Channel.class);
  }

  public void updateCorrelationId(String channelId, String correlationId) {
    Query query = new Query(Criteria.where("_id").is(channelId));
    Update update = new Update().set("correlationId", correlationId)
        .set("updatedDate", new Date());
    mongoTemplate.updateFirst(query, update, Channel.class);
  }

  public void deleteChannel(String channelId) {
    channelRepository.deleteById(channelId);
  }

  public List<Channel> findChannelsBySubscriber(String email) {
    return channelRepository.findBySubscribersContaining(email);
  }
}
