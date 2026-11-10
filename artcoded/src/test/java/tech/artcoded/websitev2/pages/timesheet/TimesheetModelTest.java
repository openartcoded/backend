package tech.artcoded.websitev2.pages.timesheet;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import tech.artcoded.websitev2.utils.helper.DateHelper;
import tech.artcoded.websitev2.utils.helper.Json;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

class TimesheetModelTest {

    private static java.util.Date at(int hour, int minute) {
        return DateHelper.toDate(LocalDateTime.of(2026, 10, 26, hour, minute));
    }

    @Test
    void durationSumsMorningAndAfternoon() {
        var p = TimesheetPeriod.builder().morningStartTime(at(8, 30)).morningEndTime(at(12, 0))
                .afternoonStartTime(at(12, 45)).afternoonEndTime(at(17, 10)).build();
        assertThat(p.getDuration()).isEqualTo(210 + 265);
        assertThat(p.getDurationInHours()).isEqualTo("07:55");
        assertThat(p.isRowFilled()).isTrue();
    }

    @Test
    void missingTimesCountAsZero() {
        var onlyMorning = TimesheetPeriod.builder().morningStartTime(at(9, 0)).morningEndTime(at(10, 0)).build();
        assertThat(onlyMorning.getDuration()).isEqualTo(60);
        assertThat(onlyMorning.isRowFilled()).isFalse();

        var startWithoutEnd = TimesheetPeriod.builder().morningStartTime(at(9, 0)).build();
        assertThat(startWithoutEnd.getDuration()).isZero();
        assertThat(TimesheetPeriod.builder().build().getDuration()).isZero();
        assertThat(TimesheetPeriod.builder().build().getDurationInHours()).isEqualTo("00:00");
    }

    @Test
    void dayOfWeekOfThePeriod() {
        var monday = TimesheetPeriod.builder().date(DateHelper.toDate(LocalDate.of(2026, 10, 26))).build();
        assertThat(monday.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
    }

    @ParameterizedTest
    @CsvSource({ "0, 0.00", "10, 0.17", "30, 0.50", "90, 1.50", "455, 7.58", "9600, 160.00" })
    void minutesAreConvertedToDecimalHours(long minutes, String hours) {
        assertThat(Timesheet.minutesToHours(minutes)).isEqualByComparingTo(hours).hasScaleOf(2);
    }

    @Test
    void onlyWorkingDaysAreCounted() {
        var work = TimesheetPeriod.builder().periodType(PeriodType.WORKING_DAY).morningStartTime(at(9, 0))
                .morningEndTime(at(12, 30)).build();
        var sick = work.toBuilder().periodType(PeriodType.SICKNESS).build();
        var weekend = work.toBuilder().periodType(PeriodType.WEEKEND).build();
        var ts = Timesheet.builder().yearMonth(YearMonth.of(2026, 10)).periods(List.of(work, sick, weekend, work))
                .build();
        assertThat(ts.getNumberOfWorkingDays()).isEqualTo(2);
        assertThat(ts.getNumberOfMinutesWorked()).isEqualTo(420);
        assertThat(ts.getNumberOfHoursWorked()).isEqualTo("07:00");
        assertThat(ts.getNumberOfWorkingHours()).isEqualByComparingTo("7");
    }

    @Test
    void hoursWorkedIsFormattedWithMoreThanTwoDigitsWhenNeeded() {
        var day = TimesheetPeriod.builder().periodType(PeriodType.WORKING_DAY).morningStartTime(at(0, 0))
                .morningEndTime(at(23, 59)).build();
        var ts = Timesheet.builder().periods(List.of(day, day, day, day, day)).build();
        assertThat(ts.getNumberOfHoursWorked()).isEqualTo("119:55");
    }

    @Test
    void yearMonthAccessorsAndComparison() {
        var ts = Timesheet.builder().yearMonth(YearMonth.of(2026, 2)).build();
        assertThat(ts.getYear()).isEqualTo(2026);
        assertThat(ts.getMonth()).isEqualTo(2);
        assertThat(ts.getMonthEnum()).isEqualTo(Month.FEBRUARY);
        assertThat(ts.compareTo(YearMonth.of(2026, 1))).isPositive();
        assertThat(ts.compareTo(YearMonth.of(2026, 2))).isZero();
        assertThat(ts.compareTo(YearMonth.of(2026, 3))).isNegative();
    }

    @Test
    void monthEnumIsWrittenAsItsNameInJson() {
        var ts = Timesheet.builder().yearMonth(YearMonth.of(2026, 11)).build();
        // the API mapper (spring.jackson.use-jackson2-defaults: zero-based months) and the shared one (one-based)
        var api = JsonMapper.builder().disable(DateTimeFeature.ONE_BASED_MONTHS).build();
        for (var mapper : List.of(api, Json.MAPPER)) {
            var json = mapper.readTree(mapper.writeValueAsString(ts));
            assertThat(json.get("monthEnum").isString()).isTrue();
            assertThat(json.get("monthEnum").asString()).isEqualTo("NOVEMBER");
            assertThat(json.get("month").asInt()).isEqualTo(11);
        }
    }

    @Test
    void clientNameFallsBackToNotAvailable() {
        assertThat(Timesheet.builder().build().getClientNameOrNA()).isEqualTo("N/A");
        assertThat(Timesheet.builder().clientName("").build().getClientNameOrNA()).isEqualTo("N/A");
        assertThat(Timesheet.builder().clientName("ACME").build().getClientNameOrNA()).isEqualTo("ACME");
    }

    @Test
    void newTimesheetHasNoPeriodsAndAnId() {
        var ts = Timesheet.builder().build();
        assertThat(ts.getId()).isNotBlank();
        assertThat(ts.getPeriods()).isEmpty();
        assertThat(ts.isClosed()).isFalse();
    }
}
