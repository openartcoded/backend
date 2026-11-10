package tech.artcoded.websitev2.pages.dossier.report;

import java.math.BigDecimal;
import java.util.List;

import lombok.Builder;
import lombok.Value;

/**
 * What the dossier report template ({@code report} in the template) shows. Every value is ready to print (formatted
 * strings, chart geometry), never null: the templates stay simple loops, identical in jinja and FreeMarker.
 */
@Value
@Builder(toBuilder = true)
public class DossierReport implements java.io.Serializable {
    /** "October 2026" (one month), "January – October 2026", "October 2025 – September 2026" */
    String periodLabel;
    /** the period without year: "October", "January – October" */
    String periodName;
    /** a monthly report (one month) or the report of a dossier (every month of the dossier) */
    boolean singleMonth;
    /** "Monthly report", "Dossier report", "Annual report" */
    String kind;
    /** the report of a year: every dossier of the year, compared with the other years, the forecast */
    boolean annual;
    /** same as periodLabel: kept for the templates written for the monthly report only */
    String monthLabel;
    /** same as periodName: kept for the templates written for the monthly report only */
    String monthName;
    /** year of the last month of the period */
    int year;
    int previousYear;
    /** "January – October": january until the last month of the period */
    String ytdLabel;
    /** what the expenses per tag and the clients cover: the year so far (one month) or the period */
    String breakdownLabel;
    /** headers of the "this period / a year before" columns: "2026" / "2025", or "2025–26" / "2024–25" */
    String currentColumn;
    String previousColumn;
    /** dd/MM/yyyy */
    String generatedDate;

    String organizationName;
    String organizationVatNumber;
    /** data uri, empty when there is no logo */
    String logo;

    List<Kpi> kpis;
    List<Insight> insights;
    BarChart sameMonthChart;
    BarChart ytdChart;
    BarChart monthlyChart;
    /** every month of the period against the same month a year before */
    List<MonthRow> months;
    List<ShareRow> expensesPerTag;
    List<ShareRow> topClients;
    List<YearRow> history;
    /** the dossier; for an annual report, the dossiers of the year together */
    DossierFigures dossier;
    /** annual report: each dossier of the year */
    @lombok.Builder.Default
    List<DossierFigures> dossiers = List.of();
    /** annual report: where the last years lead, null when there isn't enough history */
    Forecast forecast;
    Pending pending;

    /** good: green, bad: red, neutral: grey */
    public enum Tone {
        GOOD, BAD, NEUTRAL;

        /** css class used by the templates */
        public String getCss() {
            return name().toLowerCase();
        }
    }

    @Value
    @Builder
    public static class Kpi implements java.io.Serializable {
        String label;
        String value;
        /** what it is compared to ("vs 12.000,00 € in October 2025") */
        String detail;
        /** "+12,3 %" */
        String evolution;
        String tone;
    }

    @Value
    public static class Insight implements java.io.Serializable {
        String tone;
        String text;
    }

    /** a tag (expenses) or a client (revenue): this year and the previous one, year to date */
    @Value
    @Builder
    public static class ShareRow implements java.io.Serializable {
        String name;
        String current;
        String previous;
        String evolution;
        String evolutionTone;
        /** share of the total of the year, "42,1 %" */
        String share;
        /** the share as a number (50.5 for 50,5 %) */
        double shareValue;
        /** bar widths in percent of the biggest row */
        double currentBar;
        double previousBar;
    }

    @Value
    @Builder
    public static class MonthRow implements java.io.Serializable {
        /** "October 2026" */
        String label;
        String revenue;
        /** the revenue as a number (to find the best month) */
        BigDecimal revenueValue;
        /** the same month a year before */
        String previousRevenue;
        String evolution;
        String evolutionTone;
        String expenses;
        String result;
        long invoices;
        /** bar widths in percent of the biggest month */
        double revenueBar;
        double previousBar;
    }

    @Value
    @Builder
    public static class YearRow implements java.io.Serializable {
        int year;
        /** the year of the report: figures until the month of the report only */
        boolean partial;
        String revenue;
        String expenses;
        String margin;
        String marginRate;
        /** revenue of the period of the report (the same months) that year */
        String monthRevenue;
        long invoices;
        String revenueEvolution;
        String revenueEvolutionTone;
    }

    @Value
    @Builder
    public static class DossierFigures implements java.io.Serializable {
        String name;
        String description;
        boolean closed;
        /** "Open" or "Closed on 31/12/2025" */
        String status;
        /** dates of the first and the last invoice of the dossier, empty without invoice */
        String period;
        long invoices;
        String revenue;
        long expenses;
        String expensesTotal;
        long documents;
        String result;
        /** the vat part is only filled once a vat due is set on the dossier */
        boolean vatKnown;
        String vatDue;
        String advancePayments;
        /** "VAT still to pay" / "VAT overpaid" */
        String vatBalanceLabel;
        String vatBalance;
        String vatBalanceTone;
    }

    /** where the trend of the last years leads */
    @Value
    @Builder
    public static class Forecast implements java.io.Serializable {
        /** "2023–2025 (2025 estimated at the pace of January – November)" */
        String basis;
        /** "+8,4 % a year", "–" without a first value */
        String revenueGrowth;
        String revenueGrowthTone;
        /** "+4.200,00 € a year" */
        String revenueTrend;
        String expensesGrowth;
        String expensesGrowthTone;
        String expensesTrend;
        /** the years of the basis (actual or estimated) then the projected ones */
        List<ForecastRow> rows;
        /** every year with data, then the projected ones (lighter) */
        BarChart chart;
        /** how it is computed */
        String method;
    }

    @Value
    @Builder
    public static class ForecastRow implements java.io.Serializable {
        int year;
        /** actual, estimated (the year isn't over) or projected */
        String status;
        String revenue;
        String expenses;
        String result;
        String marginRate;
    }

    /** what is not in a dossier yet */
    @Value
    @Builder
    public static class Pending implements java.io.Serializable {
        long invoices;
        String invoicesTotal;
        long expenses;
        String expensesTotal;
    }
}
