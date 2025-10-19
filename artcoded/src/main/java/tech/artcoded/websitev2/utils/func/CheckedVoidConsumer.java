package tech.artcoded.websitev2.utils.func;

import java.util.function.Consumer;

@FunctionalInterface
public interface CheckedVoidConsumer extends Runnable {
    static CheckedVoidConsumer toConsumer(CheckedVoidConsumer hack) {
        return hack::safeConsume;
    }

    void consume() throws Exception;

    default void run() {
        this.safeConsume();
    }

    default void safeConsume() {
        try {
            consume();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    default void consume(Consumer<Throwable> onError) {
        try {
            consume();
        } catch (Throwable e) {
            onError.accept(e);
        }
    }
}
