package tech.artcoded.websitev2.pages.dossier.report;

import java.util.List;

import lombok.Builder;
import lombok.Value;

/**
 * What the dossier report template ({@code report} in the template) shows. Every value is ready to print (formatted
 * strings, chart geometry), never null: the templates stay simple loops, identical in jinja and FreeMarker.
 */
@Value
@Builder(toBuilder = true)
public class DossierReport {
    /** "October 2026" */
    String monthLabel;
    /** "October" */
    String monthName;
    int year;
    int previousYear;
    /** "January – October" */
    String ytdLabel;
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
    List<ShareRow> expensesPerTag;
    List<ShareRow> topClients;
    List<YearRow> history;
    DossierFigures dossier;
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
    public static class Kpi {
        String label;
        String value;
        /** what it is compared to ("vs 12.000,00 € in October 2025") */
        String detail;
        /** "+12,3 %" */
        String evolution;
        String tone;
    }

    @Value
    public static class Insight {
        String tone;
        String text;
    }

    /** a tag (expenses) or a client (revenue): this year and the previous one, year to date */
    @Value
    @Builder
    public static class ShareRow {
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
    public static class YearRow {
        int year;
        /** the year of the report: figures until the month of the report only */
        boolean partial;
        String revenue;
        String expenses;
        String margin;
        String marginRate;
        String monthRevenue;
        long invoices;
        String revenueEvolution;
        String revenueEvolutionTone;
    }

    @Value
    @Builder
    public static class DossierFigures {
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

    /** what is not in a dossier yet */
    @Value
    @Builder
    public static class Pending {
        long invoices;
        String invoicesTotal;
        long expenses;
        String expensesTotal;
    }
}
