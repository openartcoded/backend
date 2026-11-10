package tech.artcoded.websitev2.pages.dossier.report;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.artcoded.websitev2.pages.dossier.report.DossierReportCalculatorTest.expense;
import static tech.artcoded.websitev2.pages.dossier.report.DossierReportCalculatorTest.invoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import tech.artcoded.websitev2.pages.dossier.Dossier;
import tech.artcoded.websitev2.pages.dossier.TvaAdvancePayment;
import tech.artcoded.websitev2.pages.dossier.report.DossierReport.ForecastRow;
import tech.artcoded.websitev2.pages.dossier.report.DossierReport.Insight;
import tech.artcoded.websitev2.pages.dossier.report.DossierReport.Kpi;
import tech.artcoded.websitev2.pages.fee.Fee;
import tech.artcoded.websitev2.pages.invoice.InvoiceGeneration;

class AnnualReportCalculatorTest {
    /** revenue: 2022 60k, 2023 80k, 2024 100k, 2025 90k; expenses: 10k a year */
    static final List<InvoiceGeneration> INVOICES = List.of(
            invoice("i22", LocalDate.of(2022, 6, 1), "60000", "Client A"),
            invoice("i23", LocalDate.of(2023, 6, 1), "80000", "Client A"),
            invoice("i24", LocalDate.of(2024, 6, 1), "100000", "Client A"),
            invoice("q1", LocalDate.of(2025, 2, 1), "40000", "Client A"),
            invoice("q2", LocalDate.of(2025, 5, 1), "50000", "Client B"));
    static final List<Fee> EXPENSES = List.of(expense("f22", LocalDate.of(2022, 6, 1), "10000", "Car"),
            expense("f23", LocalDate.of(2023, 6, 1), "10000", "Car"),
            expense("f24", LocalDate.of(2024, 6, 1), "10000", "Car"),
            expense("f25", LocalDate.of(2025, 6, 1), "10000", "Car"));

    static final Dossier Q1 = Dossier.builder().id("Q1").name("Q1-2025").closed(true).invoiceIds(Set.of("q1"))
            .tvaDue(new BigDecimal("8400"))
            .advancePayments(List.of(TvaAdvancePayment.builder().advance(new BigDecimal("8000")).build())).build();
    static final Dossier Q2 = Dossier.builder().id("Q2").name("Q2-2025").closed(true).invoiceIds(Set.of("q2"))
            .feeIds(Set.of("f25")).tvaDue(new BigDecimal("8400")).build();

    static DossierReport report(int year, YearMonth to, List<InvoiceGeneration> invoices, List<Dossier> dossiers) {
        var input = new DossierReportCalculator.Input(AnnualReportCalculator.yearDossier(year, dossiers),
                YearMonth.of(year, 1), to, invoices, EXPENSES, List.of(), List.of(), LocalDate.of(2026, 1, 20));
        return AnnualReportCalculator.compute(input, dossiers);
    }

    static Kpi kpi(DossierReport report, String labelStart) {
        return report.getKpis().stream().filter(k -> k.getLabel().startsWith(labelStart)).findFirst().orElseThrow();
    }

    @Test
    void aYearIsComparedWithAllTheOthers() {
        var report = report(2025, YearMonth.of(2025, 12), INVOICES, List.of(Q1, Q2));
        assertThat(report.isAnnual()).isTrue();
        assertThat(report.getKind()).isEqualTo("Annual report");
        assertThat(report.getPeriodLabel()).isEqualTo("January – December 2025");
        assertThat(kpi(report, "Revenue January – December 2025").getValue()).isEqualTo("90.000,00 €");

        var rank = kpi(report, "Rank among 4 years");
        assertThat(rank.getValue()).isEqualTo("2nd best");
        assertThat(rank.getDetail()).isEqualTo("best: 2024 with 100.000,00 €");
        var average = kpi(report, "Average revenue of the other years");
        assertThat(average.getValue()).isEqualTo("80.000,00 €");
        assertThat(average.getEvolution()).isEqualTo("+12,5 %");
        assertThat(average.getDetail()).isEqualTo("2022–2024, vs 90.000,00 € for 2025");

        assertThat(report.getInsights()).extracting(Insight::getText).contains(
                "2025 is the 2nd best year of 4 (best: 2024 with 100.000,00 €).",
                "Revenue 12,5 % above the average of 2022–2024 (80.000,00 €).");
    }

    @Test
    void theLastThreeYearsAreProjected() {
        var report = report(2025, YearMonth.of(2025, 12), INVOICES, List.of(Q1, Q2));
        var forecast = report.getForecast();
        assertThat(forecast).isNotNull();
        assertThat(forecast.getBasis()).isEqualTo("2023–2025");
        // 80k, 100k, 90k: +5.000 a year through 90k in 2024 (the mean)
        assertThat(forecast.getRevenueTrend()).isEqualTo("+5.000,00 € a year");
        // (90 / 80)^(1/2) - 1
        assertThat(forecast.getRevenueGrowth()).isEqualTo("+6,1 % a year");
        assertThat(forecast.getRows()).extracting(ForecastRow::getYear).containsExactly(2023, 2024, 2025, 2026, 2027,
                2028);
        assertThat(forecast.getRows()).extracting(ForecastRow::getStatus).containsExactly("actual", "actual", "actual",
                "projected", "projected", "projected");
        var next = forecast.getRows().get(3);
        assertThat(next.getRevenue()).isEqualTo("100.000,00 €");
        assertThat(next.getExpenses()).isEqualTo("10.000,00 €");
        assertThat(next.getResult()).isEqualTo("90.000,00 €");
        assertThat(forecast.getRows().getLast().getRevenue()).isEqualTo("110.000,00 €");
        assertThat(kpi(report, "Projected revenue 2026").getValue()).isEqualTo("100.000,00 €");

        // every year, then the projected ones in lighter colors
        var chart = forecast.getChart();
        assertThat(chart.getAxisLabels()).extracting(BarChart.AxisLabel::getText).containsExactly("2022", "2023",
                "2024", "2025", "2026", "2027", "2028");
        assertThat(chart.getBars()).filteredOn(b -> b.getTitle().startsWith("Projected")).hasSize(6)
                .allSatisfy(b -> assertThat(b.getColor()).isIn("#93c5fd", "#fdba74"));
        assertThat(report.getInsights()).extracting(Insight::getText).anyMatch(i -> i.startsWith(
                "Over 2023–2025 revenue grows by about 6,1 % a year (+5.000,00 € a year): at this pace 100.000,00 € in 2026"));
    }

    @Test
    void theDossiersOfTheYearTogether() {
        var report = report(2025, YearMonth.of(2025, 12), INVOICES, List.of(Q2, Q1));
        assertThat(report.getDossiers()).extracting(DossierReport.DossierFigures::getName).containsExactly("Q1-2025",
                "Q2-2025");
        var year = report.getDossier();
        assertThat(year.getName()).isEqualTo("2025");
        assertThat(year.getInvoices()).isEqualTo(2);
        assertThat(year.getRevenue()).isEqualTo("90.000,00 €");
        assertThat(year.getVatDue()).isEqualTo("16.800,00 €");
        assertThat(year.getAdvancePayments()).isEqualTo("8.000,00 €");
        assertThat(year.isClosed()).isTrue();

        var openQuarter = Dossier.builder().id("Q3").name("Q3-2025").build();
        var dossier = AnnualReportCalculator.yearDossier(2025, List.of(Q1, openQuarter));
        assertThat(dossier.isClosed()).isFalse();
        assertThat(dossier.getTvaDue()).as("not every dossier has its vat").isNull();
        assertThat(dossier.getDescription()).isEqualTo("Q1-2025, Q3-2025");
    }

    @Test
    void theYearInProgressIsEstimatedAtItsPace() {
        var invoices = new ArrayList<>(INVOICES);
        invoices.add(invoice("i26", LocalDate.of(2026, 2, 1), "30000", "Client A"));
        var report = report(2026, YearMonth.of(2026, 3), invoices, List.of());
        // 30k in three months: 120k over the year, the best one
        assertThat(kpi(report, "Rank among 5 years").getValue()).isEqualTo("Best year");
        assertThat(report.getForecast().getBasis())
                .isEqualTo("2024–2026 (2026 estimated at the pace of January – March)");
        assertThat(report.getForecast().getRows().get(2).getStatus()).isEqualTo("estimated");
        assertThat(report.getForecast().getRows().get(2).getRevenue()).isEqualTo("120.000,00 €");
        assertThat(report.getForecast().getChart().getAxisLabels()).extracting(BarChart.AxisLabel::getText)
                .contains("2026*");
        assertThat(report.getInsights().getFirst().getText())
                .startsWith("2026 isn't over: its figures run until March");
    }

    @Test
    void noProjectionWithoutHistory() {
        var report = report(2022, YearMonth.of(2022, 12),
                List.of(invoice("i22", LocalDate.of(2022, 6, 1), "60000", "Client A")), List.of());
        assertThat(report.getForecast()).isNull();
        assertThat(kpi(report, "Rank among").getValue()).isEqualTo("First year");
        assertThat(report.getInsights()).extracting(Insight::getText)
                .contains("Not enough history for a projection: it needs at least two years with data.");
    }

    @Test
    void ordinals() {
        assertThat(List.of(1, 2, 3, 4, 11, 12, 13, 21, 22, 101).stream().map(AnnualReportCalculator::ordinal))
                .containsExactly("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd", "101st");
    }

    @Test
    void theYearOfADossierIsTheOneOfMostOfItsInvoices() {
        var invoices = Map.of("a", invoice("a", LocalDate.of(2025, 11, 5), "1", "x"), "b",
                invoice("b", LocalDate.of(2025, 12, 5), "1", "x"), "c",
                invoice("c", LocalDate.of(2026, 1, 3), "1", "x"));
        var q4 = Dossier.builder().invoiceIds(Set.of("a", "b", "c")).build();
        assertThat(DossierReportService.dossierYear(q4, invoices, Map.of())).isEqualTo(2025);
        var tie = Dossier.builder().invoiceIds(Set.of("b", "c")).build();
        assertThat(DossierReportService.dossierYear(tie, invoices, Map.of())).as("the earliest on a tie")
                .isEqualTo(2025);
        var expensesOnly = Dossier.builder().feeIds(Set.of("f")).build();
        assertThat(DossierReportService.dossierYear(expensesOnly, Map.of(),
                Map.of("f", expense("f", LocalDate.of(2024, 3, 1), "1", "x")))).isEqualTo(2024);
    }
}
