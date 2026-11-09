package tech.artcoded.websitev2.mongodb.converters;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import tech.artcoded.websitev2.utils.helper.DateHelper;

class MongoConvertersTest {
    private final YearMonthToLocalDateConverter writer = new YearMonthToLocalDateConverter();
    private final LocalDateToYearMonthConverter localDateReader = new LocalDateToYearMonthConverter();
    private final DateToYearMonthConverter dateReader = new DateToYearMonthConverter();

    @ParameterizedTest
    @CsvSource({ "2024-02", "2023-12", "2026-01", "2000-02" })
    void yearMonthIsStoredAsTheFirstDayOfTheMonthAndReadBack(String value) {
        var yearMonth = YearMonth.parse(value);

        var stored = writer.convert(yearMonth);

        assertThat(stored).isEqualTo(yearMonth.atDay(1));
        assertThat(localDateReader.convert(stored)).isEqualTo(yearMonth);
        // the mongo driver gives a java.util.Date back for a stored LocalDate
        assertThat(dateReader.convert(DateHelper.toDate(stored.atStartOfDay()))).isEqualTo(yearMonth);
    }

    @ParameterizedTest
    @CsvSource({ "2024-02-29", "2024-02-01", "2023-12-31" })
    void anyDayOfTheMonthIsReadAsItsMonth(LocalDate day) {
        assertThat(localDateReader.convert(day)).isEqualTo(YearMonth.from(day));
        assertThat(dateReader.convert(DateHelper.toDate(day.atTime(23, 59)))).isEqualTo(YearMonth.from(day));
    }

    @ParameterizedTest
    @CsvSource({ "2024, FEBRUARY", "2024, DECEMBER" })
    void monthBoundariesAreKept(int year, Month month) {
        var lastDay = YearMonth.of(year, month).atEndOfMonth();
        assertThat(localDateReader.convert(lastDay)).isEqualTo(YearMonth.of(year, month));
    }
}
