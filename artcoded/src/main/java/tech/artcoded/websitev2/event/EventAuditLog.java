package tech.artcoded.websitev2.event;

import lombok.extern.slf4j.Slf4j;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@ConditionalOnProperty(name = "application.events.consumerEnabled", havingValue = "true")
public class EventAuditLog extends RouteBuilder {
  @Value("${application.events.topicPublish}")
  private String topicToPublish;

  @Override
  public void configure() throws Exception {
    log.warn("Event audit log consumer is enabled.");
    fromF("jms:topic:%s", topicToPublish)
      .routeId("EventAuditLog#consume")
      .log(LoggingLevel.DEBUG, "event of type '${headers.EventType}' received: ${body}");
  }

}
