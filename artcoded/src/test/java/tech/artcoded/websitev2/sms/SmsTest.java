package tech.artcoded.websitev2.sms;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SmsTest {
    @Test void stripsFormattingWithoutChangingTheOriginalValue() {
        var sms = Sms.builder().phoneNumber("+32 (0) 123 / 45.67\n").message("hello").build();
        assertThat(sms.getCleanPhoneNumber()).isEqualTo("+321234567");
        assertThat(sms.getPhoneNumber()).isEqualTo("+32 (0) 123 / 45.67\n");
    }

    @Test void nullPhoneNumberNormalizesToEmptyString() {
        assertThat(new Sms().getCleanPhoneNumber()).isEmpty();
    }
}
