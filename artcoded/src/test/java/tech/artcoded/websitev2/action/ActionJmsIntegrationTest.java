package tech.artcoded.websitev2.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

import freemarker.template.Configuration;
import jakarta.jms.ConnectionFactory;
import tech.artcoded.websitev2.pages.mail.MailJobRepository;
import tech.artcoded.websitev2.pages.personal.PersonalInfo;
import tech.artcoded.websitev2.pages.personal.PersonalInfoService;
import tech.artcoded.websitev2.sms.SmsService;

/**
 * An action request goes from ActionService to the action route through a real (embedded) Artemis broker with the
 * real camel-jms component. Regression: camel-jms refuses to send java serialized objects (ObjectMessage) by default,
 * which broke every action at startup.
 */
@SpringBootTest(classes = ActionJmsIntegrationTest.App.class, properties = { "spring.artemis.mode=embedded",
        "spring.artemis.embedded.persistent=false",
        // no database here: database migrations (flamingock auto-configuration) are not started
        "flamingock.management-mode=UNMANAGED" })
class ActionJmsIntegrationTest {
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class App {
    }

    @Autowired
    ConnectionFactory connectionFactory;

    private DefaultCamelContext camel;
    private final List<List<ActionParameter>> runs = new CopyOnWriteArrayList<>();

    class RecordingAction implements Action {
        @Override
        public ActionResult run(List<ActionParameter> parameters) {
            runs.add(parameters);
            return actionResultBuilder(parameters).status(StatusType.SUCCESS).messages(List.of())
                    .finishedDate(new Date()).build();
        }

        @Override
        public ActionMetadata getMetadata() {
            return ActionMetadata.builder().key(getKey()).build();
        }

        @Override
        public String getKey() {
            return "JMS_ACTION";
        }
    }

    @BeforeEach
    void startCamel() throws Exception {
        var personalInfoService = mock(PersonalInfoService.class);
        when(personalInfoService.getOptional()).thenReturn(Optional.of(PersonalInfo.builder().build()));
        camel = new DefaultCamelContext();
        camel.addComponent("jms", JmsComponent.jmsComponentAutoAcknowledge(connectionFactory));
        camel.addRoutes(new ActionRouteBuilder(List.of(new RecordingAction()), personalInfoService,
                mock(MailJobRepository.class), mock(SmsService.class), new Configuration(Configuration.VERSION_2_3_32),
                mock(ActionResultRepository.class)));
        camel.start();
    }

    @AfterEach
    void stopCamel() {
        camel.stop();
    }

    @Test
    void anActionRequestGoesThroughTheBrokerAndRuns() throws InterruptedException {
        var service = new ActionService(camel.createProducerTemplate(), mock(ActionResultRepository.class), camel,
                mock(MailJobRepository.class), List.of(new RecordingAction()));
        var params = List.of(ActionParameter.builder().key("days").value("30").build());

        // non durable topic: the route subscribes asynchronously, so resend until the first delivery
        long deadline = System.currentTimeMillis() + 10_000;
        while (runs.isEmpty() && System.currentTimeMillis() < deadline) {
            service.perform("JMS_ACTION", params, false, false, false);
            Thread.sleep(250);
        }

        assertThat(runs).isNotEmpty();
        assertThat(runs.getFirst()).isEqualTo(params);
    }
}
