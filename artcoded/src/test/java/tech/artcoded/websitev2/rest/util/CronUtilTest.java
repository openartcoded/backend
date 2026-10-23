package tech.artcoded.websitev2.rest.util;

import org.junit.jupiter.api.Test;
import tech.artcoded.websitev2.utils.helper.DateHelper;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;

class CronUtilTest {
    @Test
    void acceptsSpringSixFieldCronAndRejectsInvalidExpressions() {
        assertThat(CronUtil.isValidCronExpression("0 0 9 * * MON-FRI")).isTrue();
        assertThat(CronUtil.isValidCronExpression("not a cron")).isFalse();
    }

    @Test
    void nextExecutionOccursAfterPreviousExecution() {
        var previous = DateHelper.toDate(LocalDateTime.of(2026, 10, 22, 9, 0));
        var next = CronUtil.getNextDateFromCronExpression("0 0 9 * * *", previous);
        assertThat(DateHelper.toLocalDateTime(next)).isEqualTo(LocalDateTime.of(2026, 10, 23, 9, 0));
    }

    @Test
    void invalidExpressionIsRejectedWhenComputingNextExecution() {
        assertThatThrownBy(() -> CronUtil.getNextDateFromCronExpression("invalid", new java.util.Date()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
