package tech.artcoded.websitev2.utils.helper;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MultipartFileHelperTest {
    @Test
    void emptyHandlesNullAndZeroLength() {
        assertThat(MultipartFileHelper.isEmpty(null)).isTrue();
        assertThat(MultipartFileHelper.isEmpty(new byte[0])).isTrue();
        assertThat(MultipartFileHelper.isEmpty(new byte[] { 1 })).isFalse();
    }

    @Test
    void streamContainsTheOriginalBytes() throws Exception {
        byte[] bytes = { 1, 2, 3, 4 };
        assertThat(MultipartFileHelper.getSize(bytes)).isEqualTo(4);
        try (var stream = MultipartFileHelper.getInputStream(bytes)) {
            assertThat(stream.readAllBytes()).containsExactly(bytes);
        }
    }
}
