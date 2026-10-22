package tech.artcoded.websitev2.utils.helper;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.Date;
import static org.assertj.core.api.Assertions.*;

class DateHelperRegressionTest {
    @Test void datesBetweenIsStartInclusiveAndEndExclusive() {
        var start = LocalDate.of(2024, 2, 28);
        assertThat(DateHelper.getDatesBetween(start, start.plusDays(3)))
                .containsExactly(start, start.plusDays(1), start.plusDays(2));
        assertThat(DateHelper.getDatesBetween(start, start)).isEmpty();
    }

    @Test void dateTimeRoundTripPreservesSeconds() {
        var input = LocalDateTime.of(2026, 10, 22, 14, 15, 16);
        assertThat(DateHelper.toLocalDateTime(DateHelper.toDate(input))).isEqualTo(input);
    }

    @Test void weekendDetectionDistinguishesWeekdays() {
        assertThat(DateHelper.isWeekend(LocalDate.of(2026, 10, 24))).isTrue();
        assertThat(DateHelper.isWeekend(LocalDate.of(2026, 10, 25))).isTrue();
        assertThat(DateHelper.isWeekend(LocalDate.of(2026, 10, 26))).isFalse();
    }

    @Test void formattedDatesUseParisTimeZone() {
        var instant = Date.from(Instant.parse("2026-01-15T12:34:56Z"));
        assertThat(DateHelper.getDD_MM_YYYY(instant)).isEqualTo("15-01-2026");
        assertThat(DateHelper.getYYYYMMDDAlt(instant)).isEqualTo("20260115");
        assertThat(DateHelper.getDateToString(instant)).isEqualTo("15-01-2026 13:34:56");
        assertThat(DateHelper.getICSDate(instant)).isEqualTo("20260115T133456Z");
    }

    @Test void yearAndMonthBoundariesAreConsistent() {
        assertThat(DateHelper.getFirstDayOfCurrentMonth().getDayOfMonth()).isEqualTo(1);
        assertThat(DateHelper.getFirstDayOfNextMonth()).isEqualTo(DateHelper.getFirstDayOfCurrentMonth().plusMonths(1));
        assertThat(DateHelper.getFirstDayOfCurrentYear().getDayOfYear()).isEqualTo(1);
        assertThat(DateHelper.getLastDayOfCurrentYear().getMonth()).isEqualTo(Month.DECEMBER);
    }
}
