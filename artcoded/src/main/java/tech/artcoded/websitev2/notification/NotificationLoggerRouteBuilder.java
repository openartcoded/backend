package tech.artcoded.websitev2.notification;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import static tech.artcoded.websitev2.notification.NotificationService.*;

@Component
//@Profile({"prod", "dev"})
public class NotificationLoggerRouteBuilder extends RouteBuilder {

  private final NotificationService notificationService;

  public NotificationLoggerRouteBuilder(NotificationService notificationService) {
    this.notificationService = notificationService;
  }

  @Override
  public void configure() throws Exception {
    from(NOTIFICATION_ENDPOINT)
      .routeId("notificationRoute")
      .transform().exchange(exchange -> notificationService.notify(
          exchange.getIn().getHeader(HEADER_TITLE, String.class),
          exchange.getIn().getHeader(HEADER_TYPE, String.class),
          exchange.getIn().getHeader(CORRELATION_ID, String.class)
        )
      )
      .log("${body.type} - ${body.title}");
  }
}
