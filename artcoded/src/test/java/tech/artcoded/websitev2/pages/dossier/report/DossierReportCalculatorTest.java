package tech.artcoded.websitev2.pages.dossier.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import tech.artcoded.websitev2.domain.common.RateType;
import tech.artcoded.websitev2.pages.dossier.Dossier;
import tech.artcoded.websitev2.pages.dossier.TvaAdvancePayment;
import tech.artcoded.websitev2.pages.dossier.report.DossierReport.Insight;
import tech.artcoded.websitev2.pages.dossier.report.DossierReport.Kpi;
import tech.artcoded.websitev2.pages.fee.Fee;
import tech.artcoded.websitev2.pages.invoice.BillTo;
import tech.artcoded.websitev2.pages.invoice.InvoiceGeneration;
import tech.artcoded.websitev2.pages.invoice.InvoiceRow;
import tech.artcoded.websitev2.utils.helper.DateHelper;

class DossierReportCalculatorTest {
    static final YearMonth OCTOBER_2026 = YearMonth.of(2026, 10);

    static InvoiceGeneration invoice(String id, LocalDate date, String amount, String client) {
        return InvoiceGeneration.builder().id(id).dateOfInvoice(DateHelper.toDate(date))
                .billTo(BillTo.builder().clientName(client).build())
                .invoiceTable(List.of(InvoiceRow.builder().amount(BigDecimal.ONE).amountType(RateType.DAYS)
                        .rate(new BigDecimal(amount)).rateType(RateType.DAYS).build()))
                .build();
    }

    static Fee expense(String id, LocalDate date, String amount, String tag) {
        return Fee.builder().id(id).date(DateHelper.toDate(date)).priceHVAT(new BigDecimal(amount))
                .vat(new BigDecimal(amount).multiply(new BigDecimal("0.21"))).tag(tag).build();
    }

    static final List<InvoiceGeneration> INVOICES = List.of(
            invoice("i2024-10", LocalDate.of(2024, 10, 15), "8000", "Client A"),
            invoice("i2025-03", LocalDate.of(2025, 3, 10), "9000", "Client A"),
            invoice("i2025-10", LocalDate.of(2025, 10, 12), "10000", "Client A"),
            invoice("i2026-02", LocalDate.of(2026, 2, 5), "11000", "Client A"),
            invoice("i2026-10", LocalDate.of(2026, 10, 20), "13000", "Client B"),
            // credit note
            invoice("c2026-10", LocalDate.of(2026, 10, 25), "-2000", "Client B"),
            // after the month of the report: ignored by the figures
            invoice("i2026-11", LocalDate.of(2026, 11, 3), "50000", "Client A"));

    static final List<Fee> EXPENSES = List.of(expense("f2025-10", LocalDate.of(2025, 10, 1), "1000", "Car"),
            expense("f2026-05", LocalDate.of(2026, 5, 2), "500", "Software"),
            expense("f2026-10", LocalDate.of(2026, 10, 2), "1500", "Car"));

    static final Dossier DOSSIER = Dossier.builder().id("d2026").name("2026")
            .invoiceIds(Set.of("i2026-02", "i2026-10", "c2026-10")).feeIds(Set.of("f2026-05", "f2026-10"))
            .tvaDue(new BigDecimal("5000"))
            .advancePayments(List.of(TvaAdvancePayment.builder().advance(new BigDecimal("4000")).build())).build();

    static DossierReport report() {
        return DossierReportCalculator.compute(new DossierReportCalculator.Input(DOSSIER, OCTOBER_2026, INVOICES,
                EXPENSES, List.of(invoice("pending", LocalDate.of(2026, 11, 2), "3000", "Client B")),
                List.of(expense("pending-fee", LocalDate.of(2026, 11, 1), "200", "Software")),
                LocalDate.of(2026, 11, 10)));
    }

    static Kpi kpi(DossierReport report, String labelStart) {
        return report.getKpis().stream().filter(k -> k.getLabel().startsWith(labelStart)).findFirst().orElseThrow();
    }

    @Test
    void theMonthIsComparedWithTheSameMonthOfThePreviousYear() {
        var month = kpi(report(), "Revenue October 2026");
        // 13000 - 2000 (credit note) vs 10000
        assertThat(month.getValue()).isEqualTo("11.000,00 €");
        assertThat(month.getEvolution()).isEqualTo("+10 %");
        assertThat(month.getTone()).isEqualTo("good");
        assertThat(month.getDetail()).isEqualTo("vs 10.000,00 € in October 2025");
    }

    @Test
    void theYearSoFarIsComparedWithTheSamePeriodOfThePreviousYear() {
        var report = report();
        var revenue = kpi(report, "Revenue year to date");
        assertThat(revenue.getValue()).isEqualTo("22.000,00 €");
        assertThat(revenue.getEvolution()).isEqualTo("+15,8 %");
        var expenses = kpi(report, "Expenses year to date");
        assertThat(expenses.getValue()).isEqualTo("2.000,00 €");
        assertThat(expenses.getEvolution()).isEqualTo("+100 %");
        assertThat(expenses.getTone()).as("more expenses is bad").isEqualTo("bad");
        assertThat(kpi(report, "Result year to date").getValue()).isEqualTo("20.000,00 €");
        assertThat(kpi(report, "Projected revenue 2026").getValue()).isEqualTo("26.400,00 €");
        assertThat(report.getYtdLabel()).isEqualTo("January – October");
    }

    @Test
    void whatIsDatedAfterTheMonthIsIgnored() {
        var report = report();
        assertThat(report.getHistory()).extracting(DossierReport.YearRow::getYear).containsExactly(2026, 2025, 2024);
        var current = report.getHistory().getFirst();
        assertThat(current.isPartial()).isTrue();
        assertThat(current.getRevenue()).isEqualTo("22.000,00 €");
        assertThat(current.getInvoices()).isEqualTo(3);
        var previous = report.getHistory().get(1);
        assertThat(previous.isPartial()).isFalse();
        assertThat(previous.getRevenue()).isEqualTo("19.000,00 €");
        assertThat(previous.getRevenueEvolution()).isEqualTo("+137,5 %");
        assertThat(report.getHistory().getLast().getRevenueEvolution()).isEqualTo("–");
    }

    @Test
    void insightsPointOutWhatMatters() {
        var insights = report().getInsights().stream().map(Insight::getText).toList();
        assertThat(insights).contains("Best October of the last 3 years (since 2024).",
                "1 invoice(s) for 3.000,00 € are not processed in a dossier yet.",
                "1 expense(s) for 200,00 € are not processed in a dossier yet.",
                "VAT still to pay for 2026: 1.000,00 € (due 5.000,00 €, advance payments 4.000,00 €).");
        assertThat(insights).anyMatch(i -> i.startsWith("Expenses grow faster than revenue this year (+100 %"));
        assertThat(insights).noneMatch(i -> i.contains("depends on a single client"));
    }

    @Test
    void aSingleClientAboveSixtyPercentIsADependency() {
        var report = DossierReportCalculator.compute(new DossierReportCalculator.Input(DOSSIER, OCTOBER_2026,
                List.of(invoice("a", LocalDate.of(2026, 1, 5), "9000", "Big client"),
                        invoice("b", LocalDate.of(2026, 2, 5), "1000", "Small client")),
                List.of(), List.of(), List.of(), LocalDate.of(2026, 11, 10)));
        assertThat(report.getInsights()).extracting(Insight::getText)
                .contains("Big client brings 90 % of the revenue this year: the company depends on a single client.");
        assertThat(report.getTopClients()).extracting(DossierReport.ShareRow::getName).containsExactly("Big client",
                "Small client");
        assertThat(report.getTopClients().getFirst().getCurrentBar()).isEqualTo(100);
    }

    @Test
    void chartsHaveABarPerYearAndSeries() {
        var report = report();
        var sameMonth = report.getSameMonthChart();
        assertThat(sameMonth.isEmpty()).isFalse();
        assertThat(sameMonth.getAxisLabels()).extracting(BarChart.AxisLabel::getText).containsExactly("2024", "2025",
                "2026");
        assertThat(sameMonth.getAxisLabels().getLast().isHighlighted()).isTrue();
        assertThat(sameMonth.getBars()).hasSize(6);
        // the monthly chart has no bar after october for the year of the report
        var monthly = report.getMonthlyChart();
        assertThat(monthly.getAxisLabels()).hasSize(12);
        assertThat(monthly.getBars()).hasSize(12 + 10);
        assertThat(monthly.getAxisLabels().get(9).isHighlighted()).isTrue();
    }

    @Test
    void expensesPerTagAndTheDossier() {
        var report = report();
        assertThat(report.getExpensesPerTag()).extracting(DossierReport.ShareRow::getName).containsExactly("Car",
                "Software");
        var car = report.getExpensesPerTag().getFirst();
        assertThat(car.getCurrent()).isEqualTo("1.500,00 €");
        assertThat(car.getPrevious()).isEqualTo("1.000,00 €");
        assertThat(car.getShare()).isEqualTo("75 %");

        var dossier = report.getDossier();
        assertThat(dossier.getInvoices()).isEqualTo(3);
        assertThat(dossier.getRevenue()).isEqualTo("22.000,00 €");
        assertThat(dossier.getExpensesTotal()).isEqualTo("2.000,00 €");
        assertThat(dossier.getResult()).isEqualTo("20.000,00 €");
        assertThat(dossier.isVatKnown()).isTrue();
        assertThat(dossier.getVatBalanceTone()).isEqualTo("bad");
        assertThat(dossier.getStatus()).isEqualTo("Open");
        assertThat(report.getPending().getInvoices()).isEqualTo(1);
    }

    static DossierReport periodReport(YearMonth from, YearMonth to) {
        return DossierReportCalculator.compute(new DossierReportCalculator.Input(DOSSIER, from, to, INVOICES, EXPENSES,
                List.of(), List.of(), LocalDate.of(2026, 11, 10)));
    }

    @Test
    void theReportOfEveryMonthOfADossierComparesThePeriod() {
        var report = periodReport(YearMonth.of(2026, 1), OCTOBER_2026);
        assertThat(report.isSingleMonth()).isFalse();
        assertThat(report.getPeriodLabel()).isEqualTo("January – October 2026");
        assertThat(report.getMonthLabel()).as("kept for the installed templates").isEqualTo("January – October 2026");
        assertThat(report.getBreakdownLabel()).isEqualTo("January – October 2026");
        assertThat(report.getCurrentColumn()).isEqualTo("2026");
        assertThat(report.getPreviousColumn()).isEqualTo("2025");

        var revenue = kpi(report, "Revenue January – October 2026");
        assertThat(revenue.getValue()).isEqualTo("22.000,00 €");
        assertThat(revenue.getEvolution()).isEqualTo("+15,8 %");
        assertThat(revenue.getDetail()).isEqualTo("vs 19.000,00 € in January – October 2025");
        assertThat(kpi(report, "Expenses January – October 2026").getValue()).isEqualTo("2.000,00 €");
        assertThat(kpi(report, "Result January – October 2026").getValue()).isEqualTo("20.000,00 €");
        assertThat(report.getKpis()).extracting(Kpi::getLabel).doesNotContain("Expenses year to date");
    }

    @Test
    void everyMonthOfThePeriodIsComparedWithTheSameMonthAYearBefore() {
        var report = periodReport(YearMonth.of(2026, 1), OCTOBER_2026);
        assertThat(report.getMonths()).hasSize(10).extracting(DossierReport.MonthRow::getLabel)
                .startsWith("January 2026", "February 2026").endsWith("October 2026");
        var october = report.getMonths().getLast();
        assertThat(october.getRevenue()).isEqualTo("11.000,00 €");
        assertThat(october.getPreviousRevenue()).isEqualTo("10.000,00 €");
        assertThat(october.getEvolution()).isEqualTo("+10 %");
        assertThat(october.getExpenses()).isEqualTo("1.500,00 €");
        assertThat(october.getResult()).isEqualTo("9.500,00 €");
        assertThat(october.getInvoices()).isEqualTo(2);
        var march = report.getMonths().get(2);
        assertThat(march.getRevenue()).isEqualTo("0,00 €");
        assertThat(march.getPreviousRevenue()).isEqualTo("9.000,00 €");
        assertThat(march.getEvolution()).isEqualTo("-100 %");
        assertThat(report.getMonths().get(1).getEvolution()).as("february: nothing a year before").isEqualTo("new");

        assertThat(report.getInsights()).extracting(Insight::getText)
                .contains(
                        "No processed invoice in January 2026, March 2026, April 2026, May 2026, June 2026, July 2026,"
                                + " August 2026, September 2026.")
                .anyMatch(i -> i.startsWith("Best month of the period: "));
        // the monthly chart has the months of the period, against the same months a year before
        var monthly = report.getMonthlyChart();
        assertThat(monthly.getAxisLabels()).extracting(BarChart.AxisLabel::getText).hasSize(10).startsWith("Jan")
                .endsWith("Oct");
        assertThat(monthly.getBars()).hasSize(20);
        assertThat(monthly.getLegend()).extracting(BarChart.LegendItem::getName).containsExactly("2025", "2026");
    }

    @Test
    void aPeriodOverTwoYears() {
        var report = periodReport(YearMonth.of(2025, 10), YearMonth.of(2026, 9));
        assertThat(report.getPeriodLabel()).isEqualTo("October 2025 – September 2026");
        assertThat(report.getPeriodName()).isEqualTo("October – September");
        assertThat(report.getCurrentColumn()).isEqualTo("2025–26");
        assertThat(report.getPreviousColumn()).isEqualTo("2024–25");
        // october 2025 + february 2026, against october 2024 + march 2025
        assertThat(kpi(report, "Revenue October 2025 – September 2026").getValue()).isEqualTo("21.000,00 €");
        assertThat(kpi(report, "Revenue October 2025 – September 2026").getDetail())
                .isEqualTo("vs 17.000,00 € in October 2024 – September 2025");
        assertThat(report.getMonthlyChart().getAxisLabels()).extracting(BarChart.AxisLabel::getText).hasSize(12)
                .startsWith("Oct 25").endsWith("Sep 26");
        assertThat(report.getSameMonthChart().getAxisLabels()).extracting(BarChart.AxisLabel::getText)
                .endsWith("2024–25", "2025–26");
    }

    @Test
    void aPeriodEndingBeforeItStartsIsRefused() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> periodReport(OCTOBER_2026, YearMonth.of(2026, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aFirstReportWithoutHistoryHasNoComparison() {
        var report = DossierReportCalculator.compute(new DossierReportCalculator.Input(Dossier.builder().build(),
                OCTOBER_2026, List.of(), List.of(), List.of(), List.of(), LocalDate.of(2026, 11, 10)));
        assertThat(report.getSameMonthChart().isEmpty()).isTrue();
        assertThat(report.getMonthlyChart().isEmpty()).isTrue();
        assertThat(report.getHistory()).hasSize(1);
        assertThat(kpi(report, "Revenue October 2026").getEvolution()).isEqualTo("–");
        assertThat(report.getInsights()).extracting(Insight::getText)
                .contains("No processed invoice is dated October 2026: invoice it or process it in the dossier.");
        assertThat(report.getDossier().isVatKnown()).isFalse();
    }
}
