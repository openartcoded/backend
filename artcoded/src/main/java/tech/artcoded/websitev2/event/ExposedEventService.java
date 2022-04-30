package tech.artcoded.websitev2.event;

import org.apache.camel.ProducerTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tech.artcoded.event.IEvent;

import static org.apache.camel.ExchangePattern.InOnly;
import static tech.artcoded.websitev2.api.common.Constants.EVENT_PUBLISHER_SEDA_ROUTE;

@Service
public class ExposedEventService {
  private final ProducerTemplate producerTemplate;

  public ExposedEventService(ProducerTemplate producerTemplate) {
    this.producerTemplate = producerTemplate;
  }

  @Async
  public void sendEvent(IEvent event) {
    this.producerTemplate.sendBody(EVENT_PUBLISHER_SEDA_ROUTE, InOnly, event);
  }
}
