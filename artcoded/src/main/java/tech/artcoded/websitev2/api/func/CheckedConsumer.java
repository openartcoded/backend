package tech.artcoded.websitev2.api.func;


import java.util.function.Consumer;

@FunctionalInterface
public interface CheckedConsumer<T> {
  static <F> Consumer<F> toConsumer(CheckedConsumer<F> hack) {
    return hack::safeConsume;
  }

  void consume(T value) throws Exception;

  default void safeConsume(T value) {
    try {
      consume(value);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
