package tech.artcoded.websitev2.pages.report;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.pages.mail.MailJobRepository;
import tech.artcoded.websitev2.pages.report.Post.PostStatus;
import tech.artcoded.websitev2.utils.helper.DateHelper;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    public record UnreadMeassgesCounter(PostStatus status, String subscriber, long counter) implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    @CachePut(cacheNames = "single_channel_counter_unread_messages", key = "#channelId + '_' + #subscriber")
    public long getCountForCorrelationId(String correlationId, String subscriber) {
        return channelRepository.findByCorrelationId(correlationId)
                .map(c -> c.getMessages().stream().filter(m -> !m.read() && !m.emailFrom().equals(subscriber)).count())
                .orElse(0L);
    }

    @CachePut(cacheNames = "channel_counter_unread_messages", key = "'channel_counter_unread_messages'")
    public List<UnreadMeassgesCounter> countUnreadMessage() {
        List<UnreadMeassgesCounter> result = new ArrayList<>();

        for (var status : PostStatus.values()) {
            var posts = this.postRepository.findByStatusIsOrderByUpdatedDateDesc(status, Pageable.unpaged());

            Map<String, Long> counterPerSubscriber = new HashMap<>();

            for (var post : posts.getContent()) {
                var chanOpt = channelRepository.findByCorrelationId(post.getId());
                if (chanOpt.isPresent()) {
                    var channel = chanOpt.get();
                    channel.getMessages().stream().filter(m -> !m.read()).forEach(m -> {
                        channel.getSubscribers().stream().filter(s -> !m.emailFrom().equals(s))
                                .forEach(s -> counterPerSubscriber.merge(s, 1L, Long::sum));
                    });
                }
            }

            counterPerSubscriber
                    .forEach((subscriber, count) -> result.add(new UnreadMeassgesCounter(status, subscriber, count)));
        }

        return result;
    }

    @Scheduled(cron = "0 */10 8-18 * * MON,WED,FRI")
    public void checkUnreadMessages() {
        var fiveMinutesAgo = DateHelper.toDate(LocalDateTime.now().minusMinutes(5));

        log.info("sending channel message email...");
        channelRepository.findAll().forEach(channel -> {
            Map<String, List<Channel.Message>> messages = channel.getMessages().stream().filter(
                    msg -> !msg.read() && (msg.notifyDate() == null) && msg.creationDate().before(fiveMinutesAgo))
                    .collect(Collectors.groupingBy(Channel.Message::emailFrom));
            if (!messages.isEmpty()) {
                log.info("found {} messages in channel {}", messages.size(), channel.getId());
                var post = this.postRepository.findById(channel.getCorrelationId())
                        .orElseThrow(() -> new RuntimeException("post doesnt exist for channel " + channel.getId()));
                for (var e : messages.entrySet()) {
                    mailJobRepository
                            .sendDelayedMail(
                                    channel.getSubscribers().stream().filter(s -> !e.getKey().equals(s)).toList(),
                                    "New messages: " + post.getTitle(),
                                    "<p>New messages: <br>" + e.getValue().stream().map(m -> m.content())
                                            .collect(Collectors.joining("<br>")) + "</p>",
                                    false,
                                    e.getValue().stream().flatMap(m -> m.attachmentIds().stream()).distinct().toList(),
                                    LocalDateTime.now());
                }
                var updatedMessages = channel.getMessages().stream().map(msg -> {
                    if (!msg.read() && msg.notifyDate() == null && msg.creationDate().before(fiveMinutesAgo)) {
                        return new Channel.Message(msg.id(), msg.creationDate(), msg.emailFrom(), msg.content(),
                                msg.attachmentIds(), msg.read(), new Date());
                    }
                    return msg;
                }).toList();

                channelRepository.save(channel.toBuilder().messages(updatedMessages).build());
            } else {
                log.warn("no unread message");
            }
        });
    }

    public Optional<Channel> getChannelByCorrelationId(String id) {
        return channelRepository.findByCorrelationId(id);
    }

    @CacheEvict(cacheNames = { "single_channel_counter_unread_messages",
            "channel_counter_unread_messages" }, allEntries = true)
    public Optional<Channel> updateChannel(Channel updatedChannel) {
        return channelRepository
                .findById(updatedChannel.getId()).map(existing -> existing.toBuilder().updatedDate(new Date())
                        .messages(updatedChannel.getMessages()).subscribers(updatedChannel.getSubscribers()).build())
                .map(channelRepository::save);

    }

    @CacheEvict(cacheNames = { "single_channel_counter_unread_messages",
            "channel_counter_unread_messages" }, allEntries = true)
    public void addMessage(String channelId, Channel.Message message) {
        Query query = new Query(Criteria.where("_id").is(channelId));
        Update update = new Update().push("messages", message).set("updatedDate", new Date());
        mongoTemplate.updateFirst(query, update, Channel.class);
    }

    @CacheEvict(cacheNames = { "single_channel_counter_unread_messages",
            "channel_counter_unread_messages" }, allEntries = true)
    public void deleteMessage(String channelId, String messageId) {
        Query query = new Query(Criteria.where("_id").is(channelId));
        Update update = new Update().pull("messages", Query.query(Criteria.where("id").is(messageId)))
                .set("updatedDate", new Date());
        mongoTemplate.updateFirst(query, update, Channel.class);
    }

    @CacheEvict(cacheNames = { "single_channel_counter_unread_messages",
            "channel_counter_unread_messages" }, allEntries = true)
    public void updateCorrelationId(String channelId, String correlationId) {
        Query query = new Query(Criteria.where("_id").is(channelId));
        Update update = new Update().set("correlationId", correlationId).set("updatedDate", new Date());
        mongoTemplate.updateFirst(query, update, Channel.class);
    }

    @CacheEvict(cacheNames = { "single_channel_counter_unread_messages",
            "channel_counter_unread_messages" }, allEntries = true)
    public void deleteChannel(String channelId) {
        channelRepository.deleteById(channelId);
    }

    public List<Channel> findChannelsBySubscriber(String email) {
        return channelRepository.findBySubscribersContaining(email);
    }

}
