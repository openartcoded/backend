package tech.artcoded.websitev2.utils.helper;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;

class IdGeneratorsTest {
    @Test void generatesValidDistinctUuids() {
        var ids = IntStream.range(0, 100).mapToObj(i -> IdGenerators.get()).toList();
        assertThat(ids).doesNotHaveDuplicates();
        assertThat(ids).allSatisfy(id -> assertThat(UUID.fromString(id).toString()).isEqualTo(id));
    }
}
