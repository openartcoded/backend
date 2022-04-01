package tech.artcoded.websitev2.api.func;


import java.util.function.Consumer;

@FunctionalInterface
public interface CheckedVoidConsumer {
  static CheckedVoidConsumer toConsumer(CheckedVoidConsumer hack) {
    return hack::safeConsume;
  }

  void consume() throws Exception;

  default void safeConsume() {
    try {
      consume();
    }
    catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  default void consume(Consumer<Throwable> onError) {
    try {
      consume();
    }
    catch (Throwable e) {
      onError.accept(e);
    }
  }
}
