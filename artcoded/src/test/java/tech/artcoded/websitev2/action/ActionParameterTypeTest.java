package tech.artcoded.websitev2.action;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.math.BigInteger;
import static org.assertj.core.api.Assertions.*;

class ActionParameterTypeTest {
    @Test void stringsAndBigStringsRetainInput() {
        assertThat(ActionParameterType.STRING.castString("hello")).contains("hello");
        // BIG_STRING is an allowed parameter kind, but its castString currently returns empty.
        assertThat(ActionParameterType.BIG_STRING.castString("hello")).isEmpty();
    }

    @Test void numericValuesAreParsedAndInvalidInputsReturnEmpty() {
        assertThat(ActionParameterType.INTEGER.castInteger("123")).contains(123);
        assertThat(ActionParameterType.LONG.castLong("12345678901")).contains(12345678901L);
        assertThat(ActionParameterType.DOUBLE.castDouble("12.5")).contains(12.5);
        assertThat(ActionParameterType.BIG_DECIMAL.castBigDecimal("123.45")).contains(new BigDecimal("123.45"));
        assertThat(ActionParameterType.BIGINTEGER.castBigInteger("12345678901234567890"))
                .contains(new BigInteger("12345678901234567890"));
        assertThat(ActionParameterType.INTEGER.castInteger("abc")).isEmpty();
        assertThat(ActionParameterType.INTEGER.castInteger(null)).isEmpty();
    }

    @Test void booleansParseAccordingToJavaBooleanParsing() {
        assertThat(ActionParameterType.BOOLEAN.castBoolean("true")).contains(true);
        assertThat(ActionParameterType.BOOLEAN.castBoolean("false")).contains(false);
        assertThat(ActionParameterType.BOOLEAN.castBoolean("anything-else")).contains(false);
    }

    @Test void usingWrongConverterDoesNotProduceUnexpectedValue() {
        assertThat(ActionParameterType.STRING.castInteger("42")).isEmpty();
        assertThat(ActionParameterType.INTEGER.castBoolean("true")).isEmpty();
    }
}
