package tech.artcoded.websitev2.event;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;

//@Component
//@Slf4j
//@ConditionalOnProperty(name = "application.events.consumerEnabled", havingValue = "true")
public class EventAuditLog extends RouteBuilder {
  @Value("${application.events.topicPublish}")
  private String topicToPublish;

  @Override
  public void configure() throws Exception {
    log.warn("Event audit log consumer is enabled.");
    fromF("%s", topicToPublish)
        .routeId("EventAuditLog#consume")
        .log(LoggingLevel.DEBUG, "event of type '${headers.EventType}' received: ${body}");
  }

}
