package tech.artcoded.websitev2.messaging;

import jakarta.jms.JMSException;
import jakarta.jms.TextMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jms.core.JmsTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** Boots Artemis in-process: no Docker, network broker or external infrastructure. */
@SpringBootTest(classes = EmbeddedArtemisIntegrationTest.App.class, properties = {
        "spring.artemis.mode=embedded",
        "spring.artemis.embedded.persistent=false",
        "spring.artemis.embedded.queues=regression.queue",
        "spring.jms.pub-sub-domain=false",
        "spring.jms.template.receive-timeout=2000"
})
class EmbeddedArtemisIntegrationTest {
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class App { }

    @Autowired JmsTemplate jms;

    @Test void textMessageRoundTripsThroughActualBroker() throws JMSException {
        jms.convertAndSend("regression.queue", "spring-artemis-round-trip");
        var message = jms.receive("regression.queue");
        assertThat(message).isInstanceOf(TextMessage.class);
        assertThat(((TextMessage) message).getText()).isEqualTo("spring-artemis-round-trip");
    }

    @Test void springMessageConverterRoundTripsString() {
        jms.convertAndSend("regression.queue", "converted-payload");
        assertThat(jms.receiveAndConvert("regression.queue")).isEqualTo("converted-payload");
    }
}
