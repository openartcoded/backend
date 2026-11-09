package tech.artcoded.websitev2.action;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RegExUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public enum ActionParameterType {
    INTEGER, LONG, STRING, BIG_STRING, BOOLEAN, DOUBLE, BIG_DECIMAL, BIGINTEGER, DATE, DATE_STRING, OPTION;

    public Optional<Long> castLong(String value) {
        return cast(Long::parseLong, sanitizeNumber(value), LONG);
    }

    public Optional<Integer> castInteger(String value) {
        return cast(Integer::parseInt, sanitizeNumber(value), INTEGER);
    }

    public Optional<String> castString(String value) {
        return cast(v -> v, value, STRING, BIG_STRING);

    }

    public Optional<Boolean> castBoolean(String value) {
        return cast(Boolean::parseBoolean, value, BOOLEAN);
    }

    public Optional<Double> castDouble(String value) {
        return cast(Double::parseDouble, sanitizeNumber(value), DOUBLE);

    }

    public Optional<BigDecimal> castBigDecimal(String value) {
        return cast(BigDecimal::new, sanitizeNumber(value), BIG_DECIMAL);

    }

    public Optional<BigInteger> castBigInteger(String value) {
        return cast(BigInteger::new, sanitizeNumber(value), BIGINTEGER);
    }

    private void checkParameter(ActionParameterType parameterType, ActionParameterType... expectedTypes) {
        var types = Arrays.asList(expectedTypes);
        if (!types.contains(parameterType)) {
            throw new RuntimeException("parameter type is not of type "
                    + types.stream().map(t -> t.name()).collect(Collectors.joining(",")));
        }
    }

    /**
     * Keeps the digits, the decimal point and a leading minus sign ("-1 234.5 EUR" -> "-1234.5"). The previous
     * implementation dropped the minus sign, turning negative values into positive ones.
     */
    private static String sanitizeNumber(String value) {
        if (value == null) {
            return null;
        }
        var trimmed = value.strip();
        var negative = trimmed.startsWith("-");
        var digits = RegExUtils.removeAll(trimmed, "[^\\d.]");
        return negative && !digits.isEmpty() ? "-" + digits : digits;
    }

    private <T> Optional<T> cast(Function<String, T> castFunction, String input,
            ActionParameterType... expectedTypes) {
        try {
            checkParameter(this, expectedTypes);
            return Optional.ofNullable(input).map(castFunction);
        } catch (Exception e) {
            log.error("casting error", e);
        }
        return Optional.empty();

    }
}
