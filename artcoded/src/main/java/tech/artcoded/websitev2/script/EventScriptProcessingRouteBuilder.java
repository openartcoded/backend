package tech.artcoded.websitev2.script;

import org.apache.camel.Body;
import org.apache.camel.Header;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.notification.NotificationService;
import tech.artcoded.websitev2.utils.common.Constants;

@Component
@Slf4j
public class EventScriptProcessingRouteBuilder extends RouteBuilder {
  private static final String NOTIFICATION_SCRIPT_EXCEPTION = "NOTIFICATION_SCRIPT_EXCEPTION";
  @Value("${application.events.topicPublish}")
  private String topicToConsume;

  private final ScriptService scriptService;
  private final NotificationService notificationService;

  public EventScriptProcessingRouteBuilder(ScriptService scriptService, NotificationService notificationService) {
    this.scriptService = scriptService;
    this.notificationService = notificationService;
  }

  @Override
  public void configure() throws Exception {
    fromF("%s", topicToConsume)
        .routeId("EventScriptProcessing#consume")
        .log(LoggingLevel.DEBUG, "receive event of type '${headers.EventType}', processing...")
        .bean(() -> this, "processEvent");

  }

  public void processEvent(@Header(Constants.EVENT_TYPE) String eventType, @Body String event) {
    for (var script : scriptService.getScripts()) {
      if (!script.isEnabled() || !script.isConsumeEvent()) {
        continue;
      }
      try {
        log.info("send event of type {} to script with name {}", eventType, script.getName());
        var result = script.getProcessMethod().execute(event);
        log.debug("result {}", result);
      } catch (Exception ex) {
        log.error("an error occurred while processing script", ex);
        notificationService.sendEvent("Error: " + script.getName(), NOTIFICATION_SCRIPT_EXCEPTION, script.getId());
      }
    }
  }

}
