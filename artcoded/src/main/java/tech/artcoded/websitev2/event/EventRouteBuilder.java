package tech.artcoded.websitev2.event;

import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tech.artcoded.event.IEvent;

import static tech.artcoded.websitev2.api.common.Constants.EVENT_PUBLISHER_SEDA_ROUTE;

@Component
public class EventRouteBuilder extends RouteBuilder {
  @Value("${application.events.topicPublish}")
  private String topicToPublish;

  @Override
  public void configure() throws Exception {
    from(EVENT_PUBLISHER_SEDA_ROUTE)
      .routeId("EventRouteBuilder#publish-new-event")
      .filter(body().isInstanceOf(IEvent.class))
      .setHeader("EventType", simple("${body.eventName}"))
      .marshal().json(JsonLibrary.Jackson)
      .to(ExchangePattern.InOnly, "jms:topic:" + topicToPublish);
  }
}
