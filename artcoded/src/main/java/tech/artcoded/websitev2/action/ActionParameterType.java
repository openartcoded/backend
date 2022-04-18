package tech.artcoded.websitev2.action;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RegExUtils;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;
import java.util.function.Function;

@Slf4j
public enum ActionParameterType implements Serializable {
  INTEGER, LONG, STRING, BOOLEAN, DOUBLE, BIG_DECIMAL, BIGINTEGER, DATE, DATE_STRING, OPTION;

  public Optional<Long> castLong(String value) {
    return cast(Long::parseLong, LONG, sanitizeNumber(value));
  }

  public Optional<Integer> castInteger(String value) {
    return cast(Integer::parseInt, INTEGER, sanitizeNumber(value));
  }

  public Optional<String> castString(String value) {
    checkParameter(this, ActionParameterType.STRING);
    return cast(v -> v, STRING, value);

  }

  public Optional<Boolean> castBoolean(String value) {
    return cast(Boolean::parseBoolean, BOOLEAN, value);
  }

  public Optional<Double> castDouble(String value) {
    return cast(Double::parseDouble, DOUBLE, sanitizeNumber(value));

  }

  public Optional<BigDecimal> castBigDecimal(String value) {
    return cast(BigDecimal::new, BIG_DECIMAL, sanitizeNumber(value));

  }

  public Optional<BigInteger> castBigInteger(String value) {
    return cast(BigInteger::new, BIGINTEGER, sanitizeNumber(value));
  }

  private void checkParameter(ActionParameterType parameterType, ActionParameterType expectedType) {
    if (!expectedType.equals(parameterType)) {
      throw new RuntimeException("parameter type is not of type " + expectedType.name());
    }
  }

  private String sanitizeNumber(String value) {
    return RegExUtils.removeAll(value, "-?[^\\d.]");
  }

  private <T> Optional<T> cast(Function<String, T> castFunction, ActionParameterType expectedType, String input) {
    try {
      checkParameter(this, expectedType);
      return Optional.ofNullable(input)
        .map(castFunction);
    } catch (Exception e) {
      log.error("casting error", e);
    }
    return Optional.empty();

  }
}
