package tech.artcoded.websitev2.pages.businessplan;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import tech.artcoded.websitev2.pages.businessplan.BusinessPlanFigures.ExpenseLine;
import tech.artcoded.websitev2.pages.businessplan.BusinessPlanFigures.InvoiceLine;
import tech.artcoded.websitev2.pages.businessplan.BusinessPlanFigures.Year;
import tech.artcoded.websitev2.pages.businessplan.BusinessPlanRequest.Language;

class BusinessPlanCalculatorTest {
    static final LocalDate TODAY = LocalDate.of(2027, 3, 10);
    static final BusinessPlanRequest NO_LOAN = new BusinessPlanRequest(Language.EN, null, null, null, null, null);

    static InvoiceLine invoice(int year, int month, String revenue) {
        return new InvoiceLine(year, month, new BigDecimal(revenue));
    }

    static ExpenseLine expense(int year, String tag, String amount, boolean exceptional) {
        return new ExpenseLine(LocalDate.of(year, 6, 1), "expense " + tag, tag, new BigDecimal(amount), exceptional,
                exceptional);
    }

    // 2024: 100000, 2025: 110000 (+10%), 2026: 132000 (+20%), 2027 in progress
    static final List<InvoiceLine> INVOICES = List.of(invoice(2024, 1, "50000"), invoice(2024, 7, "50000"),
            invoice(2025, 1, "10000"), invoice(2025, 2, "10000"), invoice(2025, 9, "90000"),
            invoice(2026, 1, "12000"), invoice(2026, 2, "13000"), invoice(2026, 3, "7000"),
            invoice(2026, 10, "100000"), invoice(2027, 1, "15000"), invoice(2027, 3, "15000"),
            invoice(2027, 4, "99999"));

    static final List<ExpenseLine> EXPENSES = List.of(expense(2024, "OFFICE", "10000", false),
            expense(2025, "OFFICE", "12000", false), expense(2025, "TAX", "30000", true),
            expense(2026, "OFFICE", "14000", false), expense(2026, "CAR", "6000", false),
            expense(2026, "TAX", "15000", true), expense(2027, "OFFICE", "2000", false));

    static BusinessPlanFigures figures(BusinessPlanRequest request) {
        return BusinessPlanCalculator.compute(INVOICES, EXPENSES, new BigDecimal("1000"), request, TODAY);
    }

    @Test
    void theYearsMostRecentFirstWithTheirGrowthAndMargin() {
        var f = figures(NO_LOAN);
        assertThat(f.getYears()).extracting(Year::getYear).containsExactly(2027, 2026, 2025, 2024);
        var y2027 = f.getYears().get(0);
        assertThat(y2027.isPartial()).isTrue();
        // a year in progress is not compared with a whole year
        assertThat(y2027.getGrowth()).isNull();

        var y2026 = f.getYears().get(1);
        assertThat(y2026.getRevenue()).isEqualByComparingTo("132000");
        assertThat(y2026.getGrowth()).isEqualByComparingTo("0.2");
        assertThat(y2026.getNormalExpenses()).isEqualByComparingTo("20000");
        assertThat(y2026.getExceptionalExpenses()).isEqualByComparingTo("15000");
        assertThat(y2026.getResult()).isEqualByComparingTo("97000");
        assertThat(y2026.getAdjustedResult()).isEqualByComparingTo("112000");
        assertThat(y2026.getMargin()).isEqualByComparingTo("0.7348");
        assertThat(y2026.getMonthlyRevenue()).hasSize(12);
        assertThat(y2026.getMonthlyRevenue().get(0)).isEqualByComparingTo("12000");
        assertThat(y2026.getMonthlyRevenue().get(9)).isEqualByComparingTo("100000");
        assertThat(y2026.getExpensesPerTag()).containsOnlyKeys("CAR", "OFFICE");

        assertThat(f.getYears().get(2).getGrowth()).isEqualByComparingTo("0.1");
        // the first year has nothing to compare with
        assertThat(f.getYears().get(3).getGrowth()).isNull();
    }

    @Test
    void theYearToDateComparedWithTheSameMonthsOfThePreviousYear() {
        var f = figures(NO_LOAN);
        // until march: 15000 + 15000 (april is not counted)
        assertThat(f.getYearToDate()).isEqualByComparingTo("30000");
        assertThat(f.getYearToDatePrevious()).isEqualByComparingTo("32000");
    }

    @Test
    void theProjectionsStartFromTheLastCompleteYearAndTheAverageGrowth() {
        var f = figures(NO_LOAN);
        assertThat(f.getBaseYears()).containsExactly(2026, 2025, 2024);
        assertThat(f.getForecastYears()).containsExactly(2028, 2029, 2030);
        // (0.2 + 0.1) / 2
        assertThat(f.getHistoricalGrowth()).isEqualByComparingTo("0.15");

        var realistic = f.getScenarios().get(1);
        assertThat(realistic.getKey()).isEqualTo("REALISTIC");
        // capped to 10%
        assertThat(realistic.getGrowth()).isEqualByComparingTo("0.10");
        assertThat(realistic.getRevenue()).extracting(BigDecimal::toPlainString).containsExactly("145200.00",
                "159720.00", "175692.00");
        // 20000 indexed by 2% a year
        assertThat(realistic.getNormalExpenses().get(0)).isEqualByComparingTo("20400");
        // exceptional: 0, 30000, 15000
        assertThat(realistic.getExceptionalProvision()).isEqualByComparingTo("15000");
        assertThat(realistic.getResult().get(0)).isEqualByComparingTo("109800");
        assertThat(realistic.getMargin().get(0)).isEqualByComparingTo("0.7562");
        assertThat(realistic.getDebtService()).isEmpty();

        var prudent = f.getScenarios().get(0);
        assertThat(prudent.getGrowth()).isEqualByComparingTo("0");
        // the lowest revenue of the base years, flat
        assertThat(prudent.getRevenue()).allSatisfy(r -> assertThat(r).isEqualByComparingTo("100000"));
        // indexed by 4%
        assertThat(prudent.getNormalExpenses().get(0)).isEqualByComparingTo("20800");
        assertThat(prudent.getResult().get(0)).isEqualByComparingTo("64200");

        var optimistic = f.getScenarios().get(2);
        assertThat(optimistic.getGrowth()).isEqualByComparingTo("0.15");
        assertThat(optimistic.getRevenue().get(0)).isEqualByComparingTo("151800");
    }

    @Test
    void theGrowthIsCappedBothWays() {
        assertThat(BusinessPlanCalculator.cappedGrowth(new BigDecimal("-0.4"))).isEqualByComparingTo("-0.10");
        assertThat(BusinessPlanCalculator.cappedGrowth(new BigDecimal("0.03"))).isEqualByComparingTo("0.03");
        assertThat(BusinessPlanCalculator.cappedGrowth(null)).isEqualByComparingTo("0");
    }

    @Test
    void theExceptionalExpensesMostRecentFirst() {
        var f = figures(NO_LOAN);
        assertThat(f.getExceptionalExpenses()).extracting(e -> e.date().getYear()).containsExactly(2026, 2025);
    }

    @Test
    void aLoanPaidMonthlyAndItsCoverage() {
        var f = figures(new BusinessPlanRequest(Language.FR, null, new BigDecimal("30000"), 18, new BigDecimal("6"),
                "car"));
        var loan = f.getLoan();
        // 30000 at 0.5% a month over 18 months
        assertThat(loan.getMonthlyPayment()).isEqualByComparingTo("1746.95");
        assertThat(loan.getTotalInterest()).isEqualByComparingTo("1445.10");
        var realistic = f.getScenarios().get(1);
        assertThat(realistic.getDebtService()).hasSize(3);
        assertThat(realistic.getDebtService().get(0)).isEqualByComparingTo("20963.40");
        assertThat(realistic.getDebtService().get(1)).isEqualByComparingTo("10481.70");
        assertThat(realistic.getDebtService().get(2)).isEqualByComparingTo("0");
        // 109800 / 20963.40
        assertThat(realistic.getCoverage().get(0)).isEqualByComparingTo("5.24");
        assertThat(realistic.getCoverage().get(2)).isNull();
    }

    @Test
    void aLoanWithoutInterest() {
        var loan = BusinessPlanCalculator
                .loan(new BusinessPlanRequest(Language.EN, null, new BigDecimal("1200"), 12, null, null));
        assertThat(loan.getMonthlyPayment()).isEqualByComparingTo("100");
        assertThat(loan.getTotalInterest()).isEqualByComparingTo("0");
    }

    @Test
    void noProjectionWithoutCompleteYear() {
        var f = BusinessPlanCalculator.compute(List.of(invoice(2027, 1, "7000")), List.of(), new BigDecimal("1000"),
                NO_LOAN, TODAY);
        assertThat(f.getYears()).hasSize(1);
        assertThat(f.getScenarios()).isEmpty();
        assertThat(f.getBaseYears()).isEmpty();
        assertThat(f.getHistoricalGrowth()).isNull();
    }

    @Test
    void theCacheKeyChangesWithTheRequest() {
        var a = new BusinessPlanRequest(Language.EN, "it", new BigDecimal("1000.00"), 12, null, null);
        var b = new BusinessPlanRequest(Language.EN, "it", new BigDecimal("1000"), 12, null, null);
        var c = new BusinessPlanRequest(Language.FR, "it", new BigDecimal("1000"), 12, null, null);
        assertThat(a.cacheKey()).isEqualTo(b.cacheKey());
        assertThat(a.cacheKey()).isNotEqualTo(c.cacheKey());
        assertThat(new BusinessPlanRequest(null, null, null, null, null, null).languageOrDefault())
                .isEqualTo(Language.EN);
    }
}
