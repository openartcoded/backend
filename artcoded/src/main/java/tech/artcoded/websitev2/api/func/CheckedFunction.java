package tech.artcoded.websitev2.api.func;


import java.util.function.Function;

@FunctionalInterface
public interface CheckedFunction<I, O> {
  static <I, O> Function<I, O> toFunction(CheckedFunction<I, O> hack) {
    return hack::safeApply;
  }

  O apply(I input) throws Exception;

  default O safeApply(I input) {
    try {
      return apply(input);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
