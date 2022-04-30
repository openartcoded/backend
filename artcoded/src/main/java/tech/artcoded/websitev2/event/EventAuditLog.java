package tech.artcoded.websitev2.event;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EventAuditLog extends RouteBuilder {
  @Value("${application.events.topicPublish}")
  private String topicToPublish;

  @Override
  public void configure() throws Exception {
    fromF("jms:topic:%s", topicToPublish)
      .log(LoggingLevel.INFO, "event received: ${body}");
  }

}
