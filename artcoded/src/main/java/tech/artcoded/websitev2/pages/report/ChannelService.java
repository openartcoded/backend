package tech.artcoded.websitev2.pages.report;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.pages.mail.MailJobRepository;
import tech.artcoded.websitev2.utils.helper.DateHelper;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChannelService {

  private final ChannelRepository channelRepository;
  private final MongoTemplate mongoTemplate;
  private final MailJobRepository mailJobRepository;
  private final PostRepository postRepository;

  public Channel createChannel(String correlationId) {
    return channelRepository.save(Channel.builder().correlationId(correlationId).build());
  }

  public Optional<Channel> getChannel(String id) {
    return channelRepository.findById(id);
  }

  @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
  public void checkUnreadMessages() {
    var fiveMinutesAgo = DateHelper.toDate(LocalDateTime.now().minusMinutes(5));

    channelRepository.findAll().forEach(channel -> {
      log.info("sending channel message email...");
      Map<String, List<Channel.Message>> messages = channel.getMessages().stream()
          .filter(msg -> !msg.read() && msg.creationDate().before(fiveMinutesAgo))
          .collect(Collectors.groupingBy(Channel.Message::emailFrom));
      if (!messages.isEmpty()) {
        var post = this.postRepository.findById(channel.getCorrelationId())
            .orElseThrow(() -> new RuntimeException("post doesnt exist for channel " + channel.getId()));
        for (var e : messages.entrySet()) {
          mailJobRepository.sendDelayedMail(List.of(e.getKey()), "New messages: " + post.getTitle(),
              "<p>New messages: <br>" + e.getValue().stream().map(m -> m.content()).collect(Collectors.joining("<br>"))
                  + "</p>",
              false, e.getValue().stream().flatMap(m -> m.attachmentIds().stream()).distinct().toList(),
              LocalDateTime.now());
        }

      }
    });
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
