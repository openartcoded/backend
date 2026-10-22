package tech.artcoded.websitev2.pages.timesheet;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import tech.artcoded.websitev2.domain.common.RateType;
import tech.artcoded.websitev2.pages.client.BillableClient;
import tech.artcoded.websitev2.pages.client.ClientRate;
import tech.artcoded.websitev2.utils.helper.DateHelper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

public class TimesheetPeriodRateTest {

    private static final ClientRate STANDARD = ClientRate.builder().id("std").rate(new BigDecimal("50"))
            .rateType(RateType.HOURS).build();
    private static final ClientRate SUPPORT = ClientRate.builder().id("sup").label("Weekend support")
            .rate(new BigDecimal("100")).rateType(RateType.HOURS).build();

    private static BillableClient client(ClientRate... rates) {
        return BillableClient.builder().name("ACME").nature("Consulting").projectName("Project X")
                .rates(new ArrayList<>(List.of(rates))).build();
    }

    /** 7 hours worked: 09:00-12:00 and 13:00-17:00 */
    private static TimesheetPeriod workingDay(String id, int day, String rateId) {
        var d = LocalDate.of(2026, 10, day);
        return TimesheetPeriod.builder().id(id).date(DateHelper.toDate(d)).periodType(PeriodType.WORKING_DAY)
                .rateId(rateId).morningStartTime(DateHelper.toDate(LocalDateTime.of(2026, 10, day, 9, 0)))
                .morningEndTime(DateHelper.toDate(LocalDateTime.of(2026, 10, day, 12, 0)))
                .afternoonStartTime(DateHelper.toDate(LocalDateTime.of(2026, 10, day, 13, 0)))
                .afternoonEndTime(DateHelper.toDate(LocalDateTime.of(2026, 10, day, 17, 0))).build();
    }

    private static Timesheet closedTimesheet(TimesheetPeriod... periods) {
        return Timesheet.builder().yearMonth(YearMonth.of(2026, 10)).closed(true).uploadId("upload")
                .periods(List.of(periods)).build();
    }

    @Test
    public void onlyTheRateOfTheGivenPeriodChanges() {
        var original = closedTimesheet(workingDay("p1", 1, "std"), workingDay("p2", 2, "std"));

        var updated = TimesheetService.withPeriodRate(original, "p2", "sup");

        Assertions.assertThat(updated.getPeriods()).extracting(TimesheetPeriod::getId).containsExactly("p1", "p2");
        Assertions.assertThat(updated.getPeriods()).extracting(TimesheetPeriod::getRateId).containsExactly("std",
                "sup");
        // nothing else changed
        Assertions.assertThat(updated.getPeriods().get(1)).usingRecursiveComparison().ignoringFields("rateId")
                .isEqualTo(original.getPeriods().get(1));
        Assertions.assertThat(updated.isClosed()).isTrue();
        Assertions.assertThat(updated.getUploadId()).isEqualTo("upload");
        // original untouched
        Assertions.assertThat(original.getPeriods().get(1).getRateId()).isEqualTo("std");
    }

    @Test
    public void blankRateIdMeansDefaultRate() {
        var original = closedTimesheet(workingDay("p1", 1, "sup"));
        var updated = TimesheetService.withPeriodRate(original, "p1", "  ");
        Assertions.assertThat(updated.getPeriods().get(0).getRateId()).isNull();
    }

    @Test
    public void unknownPeriodIsRejected() {
        var original = closedTimesheet(workingDay("p1", 1, "std"));
        Assertions.assertThatThrownBy(() -> TimesheetService.withPeriodRate(original, "nope", "sup"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    public void rateMustBelongToTheClient() {
        var client = client(STANDARD, SUPPORT);
        TimesheetService.checkRateBelongsToClient(client, "sup");
        TimesheetService.checkRateBelongsToClient(client, null);
        TimesheetService.checkRateBelongsToClient(client, "");
        Assertions.assertThatThrownBy(() -> TimesheetService.checkRateBelongsToClient(client, "other"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    public void legacyClientWithoutExplicitRatesIsNotValidated() {
        var legacy = BillableClient.builder().rate(new BigDecimal("400")).rateType(RateType.DAYS).build();
        TimesheetService.checkRateBelongsToClient(legacy, "whatever");
    }

    @Test
    public void totalFollowsTheRateChosenOnEachRow() {
        var client = client(STANDARD, SUPPORT);
        var ts = closedTimesheet(workingDay("p1", 1, "std"), workingDay("p2", 2, "sup"), workingDay("p5", 5, null));

        // 14h * 50 + 7h * 100
        Assertions.assertThat(TimesheetService.computeTotal(ts, client)).isEqualByComparingTo("1400.00");

        var changed = TimesheetService.withPeriodRate(ts, "p1", "sup");
        // 7h * 50 + 14h * 100
        Assertions.assertThat(TimesheetService.computeTotal(changed, client)).isEqualByComparingTo("1750.00");
    }

    @Test
    public void totalIsZeroWhenClientHasNoRate() {
        var ts = closedTimesheet(workingDay("p1", 1, null));
        Assertions.assertThat(TimesheetService.computeTotal(ts, BillableClient.builder().build()))
                .isEqualByComparingTo("0");
    }
}
