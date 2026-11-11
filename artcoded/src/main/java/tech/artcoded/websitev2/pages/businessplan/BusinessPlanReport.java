package tech.artcoded.websitev2.pages.businessplan;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Value;
import tech.artcoded.websitev2.pages.dossier.report.BarChart;

/**
 * The model of the BUSINESS_PLAN template ("plan"): every value formatted in the language of the plan, every chart with
 * its geometry. The labels are in t (t.revenue, t.secHistory...).
 */
@Value
@Builder
public class BusinessPlanReport {
    /** en, fr, nl */
    String lang;
    Map<String, String> t;
    String title;
    String subtitle;
    /** data url of the logo, empty without logo */
    String logo;

    String organizationName;
    String vatNumber;
    String address;
    String manager;
    String contact;
    /** what the company does (typed when generating), empty when not given */
    String activity;
    /** the year of the first invoice */
    String activeSince;

    List<String> summary;
    List<Kpi> kpis;

    BarChart yearsChart;
    List<YearRow> years;

    BarChart monthsChart;
    /** the years compared month by month, oldest first */
    List<String> monthYears;
    List<MonthRow> months;
    /** empty when there is no current year to compare */
    String monthsExplain;

    /** the year of the expenses table */
    String expensesYear;
    String expensesExplain;
    List<ExpenseRow> expenses;

    String exceptionalExplain;
    List<ExceptionalRow> exceptional;

    boolean hasForecast;
    List<String> forecastYears;
    /** the profit and loss of the realistic scenario */
    List<Row> forecast;
    String exceptionalForecast;
    String scenariosExplain;
    /** the result of each scenario per forecast year */
    List<Row> scenarios;

    /** null without loan */
    LoanView loan;

    List<String> method;

    @Value
    public static class Kpi {
        String label;
        String value;
        String detail;
        /** good, bad, neutral */
        String tone;
    }

    @Value
    public static class YearRow {
        String year;
        boolean partial;
        String revenue;
        String growth;
        String growthTone;
        String normalExpenses;
        String exceptionalExpenses;
        String result;
        String margin;
        boolean negative;
    }

    @Value
    public static class MonthRow {
        String month;
        /** revenue of the month per year (monthYears) */
        List<String> cells;
        /** the cumulated revenue of the current year at the end of the month, empty after the current month */
        String cumulated;
        boolean current;
    }

    @Value
    public static class ExpenseRow {
        String category;
        String amount;
        String share;
        String average;
        /** width of the bar, 0 to 100 */
        double bar;
    }

    @Value
    public static class ExceptionalRow {
        String date;
        String description;
        String category;
        String amount;
        String status;
    }

    @Value
    public static class Row {
        String label;
        List<String> cells;
        boolean strong;
        /** cells to show in red (negative), same size as cells */
        List<Boolean> negative;
    }

    @Value
    public static class LoanView {
        String amount;
        String duration;
        String rate;
        String monthlyPayment;
        String totalInterest;
        String purpose;
    }
}
