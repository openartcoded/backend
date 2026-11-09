package tech.artcoded.websitev2.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import tech.artcoded.event.IEvent;

/**
 * Contract of the events published to the outside world: every event must be (de)serializable by Jackson without
 * losing data, and must expose its version and name.
 */
class EventContractTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static Stream<Class<?>> events() throws Exception {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(IEvent.class));
        List<Class<?>> classes = scanner.findCandidateComponents("tech.artcoded.event").stream().<Class<?>>map(bd -> {
            try {
                return Class.forName(bd.getBeanClassName());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }).filter(c -> !c.isInterface() && !Modifier.isAbstract(c.getModifiers())).toList();
        return classes.stream();
    }

    @Test
    void allEventsAreDiscovered() throws Exception {
        // 29 events in event v1 when written; a failure here means the scan broke, not that an event was added
        assertThat(events().toList()).hasSizeGreaterThanOrEqualTo(29).contains(
                tech.artcoded.event.v1.invoice.InvoiceGenerated.class,
                tech.artcoded.event.v1.expense.ExpenseReceived.class);
    }

    /** Fills every field with a distinct non default value. */
    private static Object sample(Class<?> type) throws Exception {
        var instance = type.getDeclaredConstructor().newInstance();
        int i = 0;
        for (var field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            i++;
            var t = field.getType();
            Object value;
            if (t == String.class) {
                value = field.getName() + "-" + i;
            } else if (t == BigDecimal.class) {
                value = new BigDecimal(i + ".25");
            } else if (t == Date.class) {
                value = new Date(1_700_000_000_000L + i);
            } else if (t == Long.class || t == long.class) {
                value = (long) i;
            } else if (t == boolean.class || t == Boolean.class) {
                value = true;
            } else if (t == List.class) {
                value = List.of("a" + i, "b" + i);
            } else if (t == Set.class) {
                value = Set.of("s" + i);
            } else {
                throw new IllegalStateException("unsupported field type in event %s: %s".formatted(type, t));
            }
            field.set(instance, value);
        }
        return instance;
    }

    @ParameterizedTest
    @MethodSource("events")
    void eventRoundTripsThroughJson(Class<?> type) throws Exception {
        var event = sample(type);
        var json = MAPPER.writeValueAsString(event);

        var tree = MAPPER.readTree(json);
        assertThat(tree.get("version").asText()).isEqualTo("V1");
        assertThat(tree.get("eventName").asText()).isEqualTo(type.getSimpleName());
        assertThat(tree.get("timestamp").asLong()).isPositive();

        // version/timestamp/eventName are read-only: they must not break deserialization
        var deserialized = MAPPER.readValue(json, type);
        assertThat(deserialized).isEqualTo(event);
    }

    @ParameterizedTest
    @MethodSource("events")
    void eventExposesVersionAndName(Class<?> type) throws Exception {
        var event = (IEvent) type.getDeclaredConstructor().newInstance();
        assertThat(event.getVersion()).isEqualTo(IEvent.Version.V1);
        assertThat(event.getEventName()).isEqualTo(type.getSimpleName());
    }

    @ParameterizedTest
    @MethodSource("events")
    void eventsHaveABuilder(Class<?> type) throws Exception {
        var builder = type.getMethod("builder").invoke(null);
        var built = builder.getClass().getMethod("build").invoke(builder);
        assertThat(built).isInstanceOf(type);
        var copy = type.getMethod("toBuilder").invoke(built);
        assertThat(copy).isNotNull();
    }
}
