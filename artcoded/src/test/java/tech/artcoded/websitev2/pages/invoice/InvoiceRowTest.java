package tech.artcoded.websitev2.pages.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import tech.artcoded.websitev2.domain.common.RateType;

class InvoiceRowTest {

    private static InvoiceRow row(String amount, RateType amountType, String rate, RateType rateType,
            String hoursPerDay) {
        return InvoiceRow.builder().amount(new BigDecimal(amount)).amountType(amountType).rate(new BigDecimal(rate))
                .rateType(rateType).hoursPerDay(new BigDecimal(hoursPerDay)).build();
    }

    @ParameterizedTest(name = "{0} {1} at {2}/{3} ({4}h/day) = {5}")
    @CsvSource({
            // amount, amountType, rate, rateType, hoursPerDay, expected
            "10, HOURS, 50, HOURS, 8.0, 500.00", //
            "2, DAYS, 50, HOURS, 8.0, 800.00", //
            "3, DAYS, 400, DAYS, 8.0, 1200.00", //
            "16, HOURS, 400, DAYS, 8.0, 800.00", //
            "7.58, HOURS, 50, HOURS, 8.0, 379.00", //
            "0, HOURS, 400, DAYS, 8.0, 0.00" })
    void totalForEveryCombinationOfUnits(String amount, RateType amountType, String rate, RateType rateType,
            String hoursPerDay, String expected) {
        assertThat(row(amount, amountType, rate, rateType, hoursPerDay).getTotal()).isEqualByComparingTo(expected)
                .hasScaleOf(2);
    }

    @Test
    void dailyRateWithNonTerminatingHourlyEquivalentDoesNotThrow() {
        // 100 / 7.5 = 13.333... used to throw "Non-terminating decimal expansion"
        var row = row("15", RateType.HOURS, "100", RateType.DAYS, "7.5");
        assertThatCode(row::getTotal).doesNotThrowAnyException();
        assertThat(row.getTotal()).isEqualByComparingTo("200.00");

        // 10 hours at 100/day with 7.5h days = 133.333... rounded down to the cent
        assertThat(row("10", RateType.HOURS, "100", RateType.DAYS, "7.5").getTotal()).isEqualByComparingTo("133.33");
    }

    @Test
    void daysAtDailyRateIgnoreHoursPerDay() {
        assertThat(row("2", RateType.DAYS, "450", RateType.DAYS, "7.6").getTotal()).isEqualByComparingTo("900.00");
    }

    @Test
    void totalIsRoundedDownToTheCent() {
        assertThat(row("1", RateType.HOURS, "10.019", RateType.HOURS, "8.0").getTotal()).isEqualByComparingTo("10.01");
    }

    @Test
    void negativeAmountsProduceNegativeTotals() {
        assertThat(row("-2", RateType.DAYS, "400", RateType.DAYS, "8.0").getTotal()).isEqualByComparingTo("-800.00");
    }

    @Test
    void builderDefaultsToPreviousMonthAndEightHoursPerDay() {
        var row = InvoiceRow.builder().build();
        assertThat(row.getHoursPerDay()).isEqualByComparingTo("8");
        assertThat(row.getPeriod())
                .isEqualTo(DateTimeFormatter.ofPattern("MM/yyyy").format(LocalDate.now().minusMonths(1)));
        assertThat(InvoiceRow.generatePeriod()).matches("\\d{2}/\\d{4}");
    }
}
