package tech.artcoded.websitev2.action;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ActionParameterTypeTest {
    @Test
    void stringsAndBigStringsRetainInput() {
        assertThat(ActionParameterType.STRING.castString("hello")).contains("hello");
        // BIG_STRING used to return empty because the cast only accepted STRING
        assertThat(ActionParameterType.BIG_STRING.castString("hello")).contains("hello");
        assertThat(ActionParameterType.STRING.castString(null)).isEmpty();
    }

    @Test
    void numericValuesAreParsedAndInvalidInputsReturnEmpty() {
        assertThat(ActionParameterType.INTEGER.castInteger("123")).contains(123);
        assertThat(ActionParameterType.LONG.castLong("12345678901")).contains(12345678901L);
        assertThat(ActionParameterType.DOUBLE.castDouble("12.5")).contains(12.5);
        assertThat(ActionParameterType.BIG_DECIMAL.castBigDecimal("123.45")).contains(new BigDecimal("123.45"));
        assertThat(ActionParameterType.BIGINTEGER.castBigInteger("12345678901234567890"))
                .contains(new BigInteger("12345678901234567890"));
        assertThat(ActionParameterType.INTEGER.castInteger("abc")).isEmpty();
        assertThat(ActionParameterType.INTEGER.castInteger(null)).isEmpty();
        assertThat(ActionParameterType.INTEGER.castInteger("")).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = { "-42;-42", " -7 ;-7", "1 234;1234", "30 days;30", "€ 12;12", "--3;-3" })
    void numbersAreSanitizedAndKeepTheirSign(String input, long expected) {
        assertThat(ActionParameterType.LONG.castLong(input)).contains(expected);
    }

    @Test
    void negativeDecimals() {
        assertThat(ActionParameterType.BIG_DECIMAL.castBigDecimal("-12.50 EUR")).contains(new BigDecimal("-12.50"));
        assertThat(ActionParameterType.DOUBLE.castDouble("-0.5")).contains(-0.5);
    }

    @Test
    void booleansParseAccordingToJavaBooleanParsing() {
        assertThat(ActionParameterType.BOOLEAN.castBoolean("true")).contains(true);
        assertThat(ActionParameterType.BOOLEAN.castBoolean("false")).contains(false);
        assertThat(ActionParameterType.BOOLEAN.castBoolean("anything-else")).contains(false);
    }

    @Test
    void usingWrongConverterDoesNotProduceUnexpectedValue() {
        assertThat(ActionParameterType.STRING.castInteger("42")).isEmpty();
        assertThat(ActionParameterType.INTEGER.castBoolean("true")).isEmpty();
        assertThat(ActionParameterType.INTEGER.castString("42")).isEmpty();
    }
}
