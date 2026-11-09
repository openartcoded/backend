package tech.artcoded.websitev2.event;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.artcoded.websitev2.utils.common.Constants.EVENT_PUBLISHER_SEDA_ROUTE;
import static tech.artcoded.websitev2.utils.common.Constants.EVENT_TYPE;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import tech.artcoded.event.v1.expense.ExpensePriceUpdated;
import tech.artcoded.event.v1.expense.ExpenseReceived;

/** Real Camel route publishing events as JSON, the JMS topic is replaced by a mock endpoint. */
class EventRouteBuilderIntegrationTest {
    private DefaultCamelContext camel;
    private MockEndpoint topic;
    private ExposedEventService service;

    @BeforeEach
    void start() throws Exception {
        camel = new DefaultCamelContext();
        var routes = new EventRouteBuilder();
        ReflectionTestUtils.setField(routes, "topicToPublish", "mock:topic");
        camel.addRoutes(routes);
        camel.start();
        topic = camel.getEndpoint("mock:topic", MockEndpoint.class);
        service = new ExposedEventService(camel.createProducerTemplate());
    }

    @AfterEach
    void stop() {
        camel.stop();
    }

    @Test
    void eventsArePublishedAsJsonWithTheirTypeInAHeader() throws Exception {
        topic.expectedMessageCount(2);
        topic.expectedHeaderValuesReceivedInAnyOrder(EVENT_TYPE, "ExpenseReceived", "ExpensePriceUpdated");

        service.sendEvent(ExpenseReceived.builder().expenseId("e1").name("train").uploadIds(List.of("u1")).build());
        service.sendEvent(ExpensePriceUpdated.builder().expenseId("e1").priceHVat(new BigDecimal("10.00"))
                .vat(new BigDecimal("2.10")).build());

        // NB: assertIsSatisfied(5, TimeUnit.SECONDS) resolves to the static varargs overload with zero endpoints,
        // which asserts nothing and returns immediately
        topic.setResultWaitTime(TimeUnit.SECONDS.toMillis(5));
        topic.assertIsSatisfied();
        var mapper = new ObjectMapper();
        var bodies = topic.getReceivedExchanges().stream().map(e -> e.getIn().getBody(String.class))
                .map(json -> {
                    try {
                        return mapper.readTree(json);
                    } catch (Exception ex) {
                        throw new IllegalStateException(ex);
                    }
                }).toList();
        // seda is asynchronous: the order on the topic is not guaranteed, look the messages up by event name
        var byName = bodies.stream().collect(Collectors.toMap(json -> json.path("eventName").asText(), json -> json));
        assertThat(byName).containsOnlyKeys("ExpenseReceived", "ExpensePriceUpdated");

        var received = byName.get("ExpenseReceived");
        assertThat(received.path("expenseId").asText()).isEqualTo("e1");
        assertThat(received.path("uploadIds").path(0).asText()).isEqualTo("u1");
        assertThat(received.path("version").asText()).isEqualTo("V1");

        var priceUpdated = byName.get("ExpensePriceUpdated");
        assertThat(priceUpdated.path("priceHVat").decimalValue()).isEqualByComparingTo("10");
        assertThat(priceUpdated.path("vat").decimalValue()).isEqualByComparingTo("2.10");
    }

    @Test
    void nonEventsAreFilteredOut() throws Exception {
        topic.expectedMessageCount(0);
        camel.createProducerTemplate().sendBody(EVENT_PUBLISHER_SEDA_ROUTE, "not an event");
        topic.setAssertPeriod(500);
        topic.assertIsSatisfied();
    }
}
