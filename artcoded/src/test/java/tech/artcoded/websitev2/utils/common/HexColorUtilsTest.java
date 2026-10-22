package tech.artcoded.websitev2.utils.common;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class HexColorUtilsTest {
    @ParameterizedTest @ValueSource(strings = {"#fff", "#ABC", "#123456", "#aAbBcC", "#000000"})
    void acceptsThreeOrSixDigitHexCodes(String value) { assertThat(HexColorUtils.isValid(value)).isTrue(); }

    @ParameterizedTest @ValueSource(strings = {"", "fff", "#12", "#1234", "#12345", "#1234567", "#ggg", " #fff", "#fff "})
    void rejectsInvalidColors(String value) { assertThat(HexColorUtils.isValid(value)).isFalse(); }
}
