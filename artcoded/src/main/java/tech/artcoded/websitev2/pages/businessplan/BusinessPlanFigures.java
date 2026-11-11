package tech.artcoded.websitev2.pages.businessplan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Value;

/** The figures of the business plan, computed from the invoices and the expenses (amounts excluding VAT). */
@Value
@Builder
public class BusinessPlanFigures {
    /** an invoice: the year and month of its date */
    public record InvoiceLine(int year, int month, BigDecimal revenue) {
    }

    /**
     * an expense, VAT excluded
     *
     * @param exceptional
     *            exceptional for the figures (confirmed, or detected and not reviewed)
     * @param reviewed
     *            flagged by hand (confirmed or denied)
     */
    public record ExpenseLine(LocalDate date, String subject, String tag, BigDecimal amount, boolean exceptional,
            boolean reviewed) {
    }

    @Value
    @Builder
    public static class Year {
        int year;
        /** the year in progress */
        boolean partial;
        BigDecimal revenue;
        /** revenue compared with the previous year (0.12 = +12%), null without previous year */
        BigDecimal growth;
        BigDecimal normalExpenses;
        BigDecimal exceptionalExpenses;
        /** revenue - every expense */
        BigDecimal result;
        /** revenue - normal expenses */
        BigDecimal adjustedResult;
        /** result / revenue, null without revenue */
        BigDecimal margin;
        /** revenue per month (index 0 = january) */
        List<BigDecimal> monthlyRevenue;
        /** normal expenses per tag */
        Map<String, BigDecimal> expensesPerTag;
    }

    @Value
    @Builder
    public static class Scenario {
        /** PRUDENT, REALISTIC, OPTIMISTIC */
        String key;
        /** yearly revenue growth applied (0 for prudent: flat revenue) */
        BigDecimal growth;
        /** per forecast year */
        List<BigDecimal> revenue;
        List<BigDecimal> normalExpenses;
        /** the average exceptional expenses of a year (taxes, one-offs), the same every year */
        BigDecimal exceptionalProvision;
        List<BigDecimal> result;
        List<BigDecimal> margin;
        /** the loan payments of each forecast year, empty without loan */
        List<BigDecimal> debtService;
        /** result / loan payments of each forecast year (null when nothing to pay), empty without loan */
        List<BigDecimal> coverage;
    }

    @Value
    @Builder
    public static class Loan {
        BigDecimal amount;
        int months;
        BigDecimal rate;
        BigDecimal monthlyPayment;
        BigDecimal totalInterest;
    }

    LocalDate date;
    /** most recent first */
    List<Year> years;
    /** the complete years the forecast is based on (at most the last three, most recent first) */
    List<Integer> baseYears;
    /** the years of the forecast */
    List<Integer> forecastYears;
    /** average yearly growth of the revenue over the base years (not capped), null when unknown */
    BigDecimal historicalGrowth;
    /** revenue of the current year until the current month, and of the same months the previous year */
    BigDecimal yearToDate;
    BigDecimal yearToDatePrevious;
    /** empty when there is no complete year yet */
    List<Scenario> scenarios;
    /** the exceptional expenses, most recent first */
    List<ExpenseLine> exceptionalExpenses;
    /** from this price (VAT included) an expense not reviewed is exceptional */
    BigDecimal exceptionalThreshold;
    Loan loan;
}
