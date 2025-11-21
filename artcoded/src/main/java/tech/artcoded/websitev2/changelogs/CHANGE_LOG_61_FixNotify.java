package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.io.IOException;
import java.util.Date;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@ChangeUnit(id = "fix-notify-date-migration", order = "61", author = "Nordine Bittich")
@Slf4j
public class CHANGE_LOG_61_FixNotify {

    @RollbackExecution
    public void rollbackExecution() {
        log.info("Rollback not implemented for notify date migration");
    }

    @Execution
    public void execute(MongoTemplate template) throws IOException {
        log.info("Starting migration: removing notifyDate from Channel and adding to Message");

        var channels = template.findAll(org.bson.Document.class, "channel");
        var now = new Date();

        for (var channel : channels) {
            var channelId = channel.get("_id");
            log.debug("Processing channel: {}", channelId);

            template.updateFirst(Query.query(where("_id").is(channelId)), new Update().unset("notifyDate"), "channel");

            var messages = (java.util.List<?>) channel.get("messages");
            if (messages != null && !messages.isEmpty()) {
                for (int i = 0; i < messages.size(); i++) {
                    var message = (org.bson.Document) messages.get(i);
                    var read = message.getBoolean("read", true);
                    if (read) {
                        template.updateFirst(
                                Query.query(
                                        where("_id").is(channelId).and("messages." + i + ".id").is(message.get("id"))),
                                new Update().set("messages." + i + ".notifyDate", now), "channel");
                    } else {
                        template.updateFirst(
                                Query.query(
                                        where("_id").is(channelId).and("messages." + i + ".id").is(message.get("id"))),
                                new Update().set("messages." + i + ".notifyDate", null), "channel");
                    }
                }
            }

        }

    }
}
