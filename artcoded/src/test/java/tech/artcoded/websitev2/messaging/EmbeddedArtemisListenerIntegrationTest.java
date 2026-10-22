package tech.artcoded.websitev2.messaging;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies that Spring JMS listener containers actually consume embedded Artemis messages. */
@SpringBootTest(classes = EmbeddedArtemisListenerIntegrationTest.App.class, properties = {
        "spring.artemis.mode=embedded",
        "spring.artemis.embedded.persistent=false",
        "spring.artemis.embedded.queues=regression.listener",
        "spring.jms.pub-sub-domain=false"
})
class EmbeddedArtemisListenerIntegrationTest {
    @Autowired JmsTemplate jms;
    @Autowired TestConsumer consumer;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableJms
    static class App {
        @Bean TestConsumer testConsumer() { return new TestConsumer(); }
    }

    static class TestConsumer {
        final LinkedBlockingQueue<String> messages = new LinkedBlockingQueue<>();

        @JmsListener(destination = "regression.listener")
        public void accept(String text) { messages.add(text); }
    }

    @Test void listenerContainerConsumesMessagesFromEmbeddedBroker() throws InterruptedException {
        jms.convertAndSend("regression.listener", "asynchronous-delivery");
        assertThat(consumer.messages.poll(10, TimeUnit.SECONDS)).isEqualTo("asynchronous-delivery");
    }
}
