package tech.artcoded.websitev2.pages.timesheet;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import tech.artcoded.websitev2.domain.common.RateType;
import tech.artcoded.websitev2.pages.client.BillableClient;
import tech.artcoded.websitev2.pages.client.ClientRate;
import tech.artcoded.websitev2.pages.invoice.InvoiceRow;
import tech.artcoded.websitev2.utils.helper.DateHelper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

public class TimesheetInvoiceRowsTest {

    private static final ClientRate STANDARD = ClientRate.builder().id("std").rate(new BigDecimal("50"))
            .rateType(RateType.HOURS).build();
    private static final ClientRate SUPPORT = ClientRate.builder().id("sup").label("Weekend support")
            .rate(new BigDecimal("100")).rateType(RateType.HOURS).build();

    private static BillableClient client(ClientRate... rates) {
        return BillableClient.builder().nature("Consulting").projectName("Project X")
                .rates(new ArrayList<>(List.of(rates))).build();
    }

    /** 7 hours worked: 09:00-12:00 and 13:00-17:00 */
    private static TimesheetPeriod workingDay(int day, String rateId) {
        return workingDay(day, rateId, 9, 0, 12, 0, 13, 0, 17, 0);
    }

    private static TimesheetPeriod workingDay(int day, String rateId, int... t) {
        var d = LocalDate.of(2026, 10, day);
        return TimesheetPeriod.builder().date(DateHelper.toDate(d)).periodType(PeriodType.WORKING_DAY).rateId(rateId)
                .morningStartTime(DateHelper.toDate(LocalDateTime.of(d.getYear(), d.getMonth(), day, t[0], t[1])))
                .morningEndTime(DateHelper.toDate(LocalDateTime.of(d.getYear(), d.getMonth(), day, t[2], t[3])))
                .afternoonStartTime(DateHelper.toDate(LocalDateTime.of(d.getYear(), d.getMonth(), day, t[4], t[5])))
                .afternoonEndTime(DateHelper.toDate(LocalDateTime.of(d.getYear(), d.getMonth(), day, t[6], t[7])))
                .build();
    }

    private static Timesheet timesheet(TimesheetPeriod... periods) {
        return Timesheet.builder().yearMonth(YearMonth.of(2026, 10)).periods(List.of(periods)).build();
    }

    @Test
    public void groupsPeriodsByRateAndAddsLabelToDescription() {
        var ts = timesheet(workingDay(1, "std"), workingDay(2, "sup"), workingDay(5, "std"), workingDay(6, "std"),
                workingDay(7, "sup"));

        List<InvoiceRow> rows = TimesheetService.buildInvoiceRows(ts, client(STANDARD, SUPPORT));

        Assertions.assertThat(rows).hasSize(2);

        var first = rows.get(0);
        Assertions.assertThat(first.getAmount()).isEqualByComparingTo("21");
        Assertions.assertThat(first.getAmountType()).isEqualTo(RateType.HOURS);
        Assertions.assertThat(first.getRate()).isEqualByComparingTo("50");
        Assertions.assertThat(first.getNature()).isEqualTo("Consulting");
        Assertions.assertThat(first.getProjectName()).isEqualTo("Project X");
        Assertions.assertThat(first.getPeriod()).isEqualTo("10/2026");
        Assertions.assertThat(first.getTotal()).isEqualByComparingTo("1050.00");

        var second = rows.get(1);
        Assertions.assertThat(second.getAmount()).isEqualByComparingTo("14");
        Assertions.assertThat(second.getRate()).isEqualByComparingTo("100");
        Assertions.assertThat(second.getNature()).isEqualTo("Consulting - Weekend support");
        Assertions.assertThat(second.getTotal()).isEqualByComparingTo("1400.00");
    }

    @Test
    public void rowsFollowTheOrderOfTheRatesOfTheClient() {
        var ts = timesheet(workingDay(1, "sup"), workingDay(2, "std"));
        var rows = TimesheetService.buildInvoiceRows(ts, client(STANDARD, SUPPORT));
        Assertions.assertThat(rows).extracting(InvoiceRow::getRate).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("50"), new BigDecimal("100"));
    }

    @Test
    public void missingOrUnknownRateFallsBackToDefaultRate() {
        var ts = timesheet(workingDay(1, null), workingDay(2, "removed-rate"), workingDay(5, "std"));
        var rows = TimesheetService.buildInvoiceRows(ts, client(STANDARD, SUPPORT));
        Assertions.assertThat(rows).hasSize(1);
        Assertions.assertThat(rows.get(0).getAmount()).isEqualByComparingTo("21");
        Assertions.assertThat(rows.get(0).getRate()).isEqualByComparingTo("50");
    }

    @Test
    public void onlyWorkingDaysAreInvoiced() {
        var holiday = workingDay(1, "sup").toBuilder().periodType(PeriodType.AUTHORIZED_HOLIDAYS).build();
        var ts = timesheet(holiday, workingDay(2, "std"));
        var rows = TimesheetService.buildInvoiceRows(ts, client(STANDARD, SUPPORT));
        Assertions.assertThat(rows).hasSize(1);
        Assertions.assertThat(rows.get(0).getAmount()).isEqualByComparingTo("7");
    }

    @Test
    public void rateWithLessThanAnHourIsSkipped() {
        // 09:00-09:30 + 13:00-13:00 = 30 minutes
        var short30 = workingDay(2, "sup", 9, 0, 9, 30, 13, 0, 13, 0);
        var ts = timesheet(workingDay(1, "std"), short30);
        var rows = TimesheetService.buildInvoiceRows(ts, client(STANDARD, SUPPORT));
        Assertions.assertThat(rows).hasSize(1);
        Assertions.assertThat(rows.get(0).getRate()).isEqualByComparingTo("50");
    }

    @Test
    public void nothingWorkedGivesOneEmptyRowWithDefaultRate() {
        var rows = TimesheetService.buildInvoiceRows(timesheet(), client(STANDARD, SUPPORT));
        Assertions.assertThat(rows).hasSize(1);
        Assertions.assertThat(rows.get(0).getAmount()).isEqualByComparingTo("0");
        Assertions.assertThat(rows.get(0).getRate()).isEqualByComparingTo("50");
    }

    @Test
    public void legacyClientWithoutRatesStillWorks() {
        var legacy = BillableClient.builder().nature("Consulting").projectName("P").rate(new BigDecimal("400"))
                .rateType(RateType.DAYS).build();
        var rows = TimesheetService.buildInvoiceRows(timesheet(workingDay(1, null), workingDay(2, null)), legacy);
        Assertions.assertThat(rows).hasSize(1);
        Assertions.assertThat(rows.get(0).getRate()).isEqualByComparingTo("400");
        Assertions.assertThat(rows.get(0).getRateType()).isEqualTo(RateType.DAYS);
        Assertions.assertThat(rows.get(0).getAmount()).isEqualByComparingTo("14");
        Assertions.assertThat(rows.get(0).getNature()).isEqualTo("Consulting");
    }
}
