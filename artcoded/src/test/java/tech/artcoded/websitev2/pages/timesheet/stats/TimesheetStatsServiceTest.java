package tech.artcoded.websitev2.pages.timesheet.stats;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;

import tech.artcoded.websitev2.pages.timesheet.PeriodType;
import tech.artcoded.websitev2.pages.timesheet.Timesheet;
import tech.artcoded.websitev2.pages.timesheet.TimesheetPeriod;

class TimesheetStatsServiceTest {

    private static Date at(int day, int hour) {
        return Date.from(LocalDateTime.of(2026, 1, day, hour, 0).atZone(ZoneId.of("Europe/Brussels")).toInstant());
    }

    /** a working day from 9 to 12 and 13 to 17: 7 hours */
    private static TimesheetPeriod day(int day) {
        return TimesheetPeriod.builder().periodType(PeriodType.WORKING_DAY).date(at(day, 0))
                .morningStartTime(at(day, 9)).morningEndTime(at(day, 12)).afternoonStartTime(at(day, 13))
                .afternoonEndTime(at(day, 17)).build();
    }

    private static Timesheet timesheet(YearMonth month, String client, TimesheetPeriod... periods) {
        return Timesheet.builder().yearMonth(month).clientName(client).periods(List.of(periods)).build();
    }

    @Test
    void sumsTheWorkingDaysPerYearMonthAndClient() {
        var weekend = TimesheetPeriod.builder().periodType(PeriodType.WEEKEND).date(at(3, 0)).morningStartTime(at(3, 9))
                .morningEndTime(at(3, 12)).build();
        var empty = TimesheetPeriod.builder().periodType(PeriodType.WORKING_DAY).date(at(6, 0)).build();
        var stats = TimesheetStatsService
                .compute(List.of(timesheet(YearMonth.of(2026, 1), "A", day(5), day(7), weekend, empty),
                        timesheet(YearMonth.of(2026, 1), "B", day(5)), timesheet(YearMonth.of(2025, 12), "A", day(8)),
                        timesheet(YearMonth.of(2025, 11), null, day(9)),
                        Timesheet.builder().clientName("no month").build()), 2030);

        assertThat(stats.years()).containsExactly(2025, 2026);
        // most worked first
        assertThat(stats.clients()).containsExactly("A", "N/A", "B");
        // not a timesheet every month: the days worked are not all known
        assertThat(stats.workedDays()).isEmpty();
        assertThat(stats.rows()).containsExactly(new TimesheetStats.Row(2025, 11, "N/A", 7 * 60, 1),
                new TimesheetStats.Row(2025, 12, "A", 7 * 60, 1), new TimesheetStats.Row(2026, 1, "A", 14 * 60, 2),
                new TimesheetStats.Row(2026, 1, "B", 7 * 60, 1));
    }

    @Test
    void theDaysWorkedOfTheYearsWithATimesheetEveryMonth() {
        var timesheets = new java.util.ArrayList<Timesheet>();
        // 2026: a timesheet every month, the 5th of january is in two timesheets (one day)
        timesheets.add(timesheet(YearMonth.of(2026, 1), "A", day(5), day(7)));
        timesheets.add(timesheet(YearMonth.of(2026, 1), "B", day(5)));
        for (int m = 2; m <= 12; m++) {
            timesheets.add(timesheet(YearMonth.of(2026, m), "A"));
        }
        // 2025: only december
        timesheets.add(timesheet(YearMonth.of(2025, 12), "A", day(8)));
        var stats = TimesheetStatsService.compute(timesheets, 2030);
        assertThat(stats.workedDays()).containsExactly(org.assertj.core.api.Assertions.entry(2026, 2L));
    }

    @Test
    void theDaysWorkedOfTheCurrentYearEvenWithoutATimesheetEveryMonth() {
        var stats = TimesheetStatsService.compute(
                List.of(timesheet(YearMonth.of(2026, 1), "A", day(5), day(7)),
                        timesheet(YearMonth.of(2026, 1), "B", day(5)), timesheet(YearMonth.of(2025, 12), "A", day(8))),
                2026);
        // 2025: only december, 2026: the year in progress
        assertThat(stats.workedDays()).containsExactly(org.assertj.core.api.Assertions.entry(2026, 2L));
    }

    @Test
    void theStudentJobsAreLeftOut() {
        var job = timesheet(YearMonth.of(2026, 1), "Shop", day(5), day(6)).toBuilder().student(true).build();
        var stats = TimesheetStatsService.compute(List.of(job, timesheet(YearMonth.of(2026, 1), "A", day(7))), 2030);
        assertThat(stats.clients()).containsExactly("A");
        assertThat(stats.rows()).extracting(TimesheetStats.Row::client).containsExactly("A");
    }

    @Test
    void noTimesheet() {
        var stats = TimesheetStatsService.compute(List.of(), 2030);
        assertThat(stats.years()).isEmpty();
        assertThat(stats.rows()).isEmpty();
        assertThat(stats.workedDays()).isEmpty();
    }
}
