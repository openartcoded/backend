package tech.artcoded.websitev2.utils.func;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class CheckedAdaptersTest {
    @Test void functionReturnsValueAndWrapsCheckedFailure() {
        assertThat(CheckedFunction.toFunction((String s) -> s.length()).apply("hello")).isEqualTo(5);
        CheckedFunction<String, String> failing = s -> { throw new IOException("function failed"); };
        assertThatThrownBy(() -> CheckedFunction.toFunction(failing).apply("x"))
                .isInstanceOf(RuntimeException.class).hasCauseInstanceOf(IOException.class);
    }

    @Test void supplierReturnsValueAndWrapsCheckedFailure() {
        assertThat(CheckedSupplier.toSupplier(() -> "ok").get()).isEqualTo("ok");
        CheckedSupplier<String> failing = () -> { throw new IOException("supplier failed"); };
        assertThatThrownBy(() -> failing.safeGet()).hasCauseInstanceOf(IOException.class);
    }

    @Test void consumerReceivesValueAndWrapsFailure() {
        var received = new AtomicReference<String>();
        CheckedConsumer.toConsumer((String value) -> received.set(value)).accept("payload");
        assertThat(received.get()).isEqualTo("payload");
        CheckedConsumer<String> failing = value -> { throw new IOException("consumer failed"); };
        assertThatThrownBy(() -> failing.safeConsume("x")).hasCauseInstanceOf(IOException.class);
    }

    @Test void voidConsumerCanPropagateOrHandleFailure() {
        CheckedVoidConsumer failing = () -> { throw new IOException("void failed"); };
        assertThatThrownBy(failing::run).hasCauseInstanceOf(IOException.class);
        var received = new AtomicReference<Throwable>();
        failing.consume(received::set);
        assertThat(received.get()).isInstanceOf(IOException.class).hasMessage("void failed");
    }
}
