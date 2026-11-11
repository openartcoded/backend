package tech.artcoded.websitev2.pages.businessplan;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.IntStream;

import tech.artcoded.websitev2.pages.businessplan.BusinessPlanFigures.ExpenseLine;
import tech.artcoded.websitev2.pages.businessplan.BusinessPlanFigures.InvoiceLine;
import tech.artcoded.websitev2.pages.businessplan.BusinessPlanFigures.Loan;
import tech.artcoded.websitev2.pages.businessplan.BusinessPlanFigures.Scenario;
import tech.artcoded.websitev2.pages.businessplan.BusinessPlanFigures.Year;

/**
 * Computes the business plan from the invoices and the expenses (no spring, no database).
 * <p>
 * The forecast (next three years) starts from the last complete year and the average growth of the revenue over the
 * last complete years (at most three), capped to {@value #MAX_GROWTH_PERCENT}% either way:
 * <ul>
 * <li>prudent: the revenue stays at the lowest level of these years, the expenses grow by
 * {@value #PRUDENT_INDEXATION_PERCENT}% a year</li>
 * <li>realistic: the revenue grows by the average growth, the expenses by {@value #INDEXATION_PERCENT}% a year
 * (indexation)</li>
 * <li>optimistic: the revenue grows by the average growth + {@value #OPTIMISTIC_EXTRA_PERCENT} points, the expenses by
 * {@value #INDEXATION_PERCENT}% a year</li>
 * </ul>
 * Every scenario keeps a provision for exceptional expenses (taxes, one-off costs): their yearly average over the same
 * years.
 */
public final class BusinessPlanCalculator {
    static final int FORECAST_YEARS = 3;
    static final int BASE_YEARS = 3;
    static final int MAX_GROWTH_PERCENT = 10;
    static final int INDEXATION_PERCENT = 2;
    static final int PRUDENT_INDEXATION_PERCENT = 4;
    static final int OPTIMISTIC_EXTRA_PERCENT = 5;
    private static final MathContext MC = MathContext.DECIMAL64;

    private BusinessPlanCalculator() {
    }

    public static BusinessPlanFigures compute(Collection<InvoiceLine> invoices, Collection<ExpenseLine> expenses,
            BigDecimal exceptionalThreshold, BusinessPlanRequest request, LocalDate today) {
        var currentYear = today.getYear();
        var yearNumbers = new TreeSet<Integer>();
        invoices.forEach(i -> yearNumbers.add(i.year()));
        expenses.stream().filter(e -> e.date() != null).forEach(e -> yearNumbers.add(e.date().getYear()));
        yearNumbers.removeIf(y -> y > currentYear);

        // oldest first to compute the growth, then most recent first
        var years = new ArrayList<Year>();
        Year previous = null;
        for (var y : yearNumbers) {
            var year = year(y, y == currentYear, invoices.stream().filter(i -> i.year() == y).toList(),
                    expenses.stream().filter(e -> e.date() != null && e.date().getYear() == y).toList(), previous);
            years.add(year);
            previous = year;
        }
        Collections.reverse(years);

        var base = years.stream().filter(y -> !y.isPartial() && y.getRevenue().signum() > 0).limit(BASE_YEARS).toList();
        var growths = base.stream().map(Year::getGrowth).filter(g -> g != null).toList();
        // the growth of the oldest base year compares with a year that is not in the base: kept, it is a real growth
        var historicalGrowth = growths.isEmpty() ? null : average(growths, 4);
        var forecastYears = IntStream.rangeClosed(1, FORECAST_YEARS).map(i -> currentYear + i).boxed().toList();
        var loan = request != null && request.hasLoan() ? loan(request) : null;
        var scenarios = base.isEmpty() ? List.<Scenario> of()
                : List.of(scenario("PRUDENT", base, historicalGrowth, loan),
                        scenario("REALISTIC", base, historicalGrowth, loan),
                        scenario("OPTIMISTIC", base, historicalGrowth, loan));

        var month = today.getMonthValue();
        var ytd = sum(invoices.stream().filter(i -> i.year() == currentYear && i.month() <= month).toList(),
                InvoiceLine::revenue);
        var ytdPrevious = sum(invoices.stream().filter(i -> i.year() == currentYear - 1 && i.month() <= month).toList(),
                InvoiceLine::revenue);

        var exceptional = expenses.stream().filter(ExpenseLine::exceptional).sorted(
                Comparator.comparing(ExpenseLine::date, Comparator.nullsLast(Comparator.<LocalDate> reverseOrder())))
                .toList();

        return BusinessPlanFigures.builder().date(today).years(years)
                .baseYears(base.stream().map(Year::getYear).toList()).forecastYears(forecastYears)
                .historicalGrowth(historicalGrowth).yearToDate(ytd).yearToDatePrevious(ytdPrevious).scenarios(scenarios)
                .exceptionalExpenses(exceptional).exceptionalThreshold(exceptionalThreshold).loan(loan).build();
    }

    static Year year(int year, boolean partial, List<InvoiceLine> invoices, List<ExpenseLine> expenses, Year previous) {
        var revenue = sum(invoices, InvoiceLine::revenue);
        var monthly = new ArrayList<BigDecimal>(Collections.nCopies(12, BigDecimal.ZERO));
        invoices.forEach(i -> monthly.set(i.month() - 1, monthly.get(i.month() - 1).add(nz(i.revenue()))));

        var normal = expenses.stream().filter(e -> !e.exceptional()).toList();
        var normalTotal = sum(normal, ExpenseLine::amount);
        var exceptionalTotal = sum(expenses.stream().filter(ExpenseLine::exceptional).toList(), ExpenseLine::amount);
        var perTag = new TreeMap<String, BigDecimal>();
        normal.forEach(e -> perTag.merge(e.tag() == null || e.tag().isBlank() ? "-" : e.tag(), nz(e.amount()),
                BigDecimal::add));
        var result = revenue.subtract(normalTotal).subtract(exceptionalTotal);
        // a year in progress is not compared with a whole year
        var growth = partial || previous == null || previous.isPartial() || previous.getRevenue().signum() <= 0 ? null
                : revenue.divide(previous.getRevenue(), 4, RoundingMode.HALF_UP).subtract(BigDecimal.ONE);

        return Year.builder().year(year).partial(partial).revenue(revenue).growth(growth).normalExpenses(normalTotal)
                .exceptionalExpenses(exceptionalTotal).result(result).adjustedResult(revenue.subtract(normalTotal))
                .margin(revenue.signum() > 0 ? result.divide(revenue, 4, RoundingMode.HALF_UP) : null)
                .monthlyRevenue(monthly).expensesPerTag(perTag).build();
    }

    /** the growth used by the realistic scenario: the historical one, capped */
    static BigDecimal cappedGrowth(BigDecimal historicalGrowth) {
        if (historicalGrowth == null) {
            return BigDecimal.ZERO;
        }
        var max = BigDecimal.valueOf(MAX_GROWTH_PERCENT, 2);
        return historicalGrowth.max(max.negate()).min(max);
    }

    static Scenario scenario(String key, List<Year> base, BigDecimal historicalGrowth, Loan loan) {
        var last = base.getFirst();
        var growth = switch (key) {
            case "PRUDENT" -> BigDecimal.ZERO;
            case "OPTIMISTIC" -> cappedGrowth(historicalGrowth).add(BigDecimal.valueOf(OPTIMISTIC_EXTRA_PERCENT, 2));
            default -> cappedGrowth(historicalGrowth);
        };
        var startRevenue = "PRUDENT".equals(key)
                ? base.stream().map(Year::getRevenue).min(Comparator.naturalOrder()).orElseThrow()
                : last.getRevenue();
        var indexation = BigDecimal.valueOf("PRUDENT".equals(key) ? PRUDENT_INDEXATION_PERCENT : INDEXATION_PERCENT, 2);
        var provision = average(base.stream().map(Year::getExceptionalExpenses).toList(), 2);

        var revenue = new ArrayList<BigDecimal>();
        var expenses = new ArrayList<BigDecimal>();
        var result = new ArrayList<BigDecimal>();
        var margin = new ArrayList<BigDecimal>();
        for (int k = 1; k <= FORECAST_YEARS; k++) {
            var r = startRevenue.multiply(BigDecimal.ONE.add(growth).pow(k, MC)).setScale(2, RoundingMode.HALF_UP);
            var e = last.getNormalExpenses().multiply(BigDecimal.ONE.add(indexation).pow(k, MC)).setScale(2,
                    RoundingMode.HALF_UP);
            var res = r.subtract(e).subtract(provision);
            revenue.add(r);
            expenses.add(e);
            result.add(res);
            margin.add(r.signum() > 0 ? res.divide(r, 4, RoundingMode.HALF_UP) : null);
        }

        var debtService = new ArrayList<BigDecimal>();
        var coverage = new ArrayList<BigDecimal>();
        if (loan != null) {
            for (int i = 0; i < FORECAST_YEARS; i++) {
                var months = Math.max(0, Math.min(12, loan.getMonths() - 12 * i));
                var service = loan.getMonthlyPayment().multiply(BigDecimal.valueOf(months)).setScale(2,
                        RoundingMode.HALF_UP);
                debtService.add(service);
                coverage.add(service.signum() > 0 ? result.get(i).divide(service, 2, RoundingMode.HALF_UP) : null);
            }
        }
        return Scenario.builder().key(key).growth(growth).revenue(revenue).normalExpenses(expenses)
                .exceptionalProvision(provision).result(result).margin(margin).debtService(debtService)
                .coverage(coverage).build();
    }

    /** a loan paid back by constant monthly payments (annuity) */
    static Loan loan(BusinessPlanRequest request) {
        var amount = request.loanAmount();
        var months = request.loanMonths();
        var yearlyRate = request.loanRate() == null ? BigDecimal.ZERO : request.loanRate();
        var monthlyRate = yearlyRate.divide(BigDecimal.valueOf(1200), MC);
        BigDecimal payment;
        if (monthlyRate.signum() == 0) {
            payment = amount.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
        } else {
            // amount * r / (1 - (1 + r)^-n)
            var factor = BigDecimal.ONE.add(monthlyRate).pow(-months, MC);
            payment = amount.multiply(monthlyRate).divide(BigDecimal.ONE.subtract(factor), 2, RoundingMode.HALF_UP);
        }
        var interest = payment.multiply(BigDecimal.valueOf(months)).subtract(amount).max(BigDecimal.ZERO);
        return Loan.builder().amount(amount).months(months).rate(yearlyRate).monthlyPayment(payment)
                .totalInterest(interest.setScale(2, RoundingMode.HALF_UP)).build();
    }

    private static <T> BigDecimal sum(Collection<T> items, Function<T, BigDecimal> value) {
        return items.stream().map(value).map(BusinessPlanCalculator::nz).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    static BigDecimal average(List<BigDecimal> values, int scale) {
        return values.isEmpty() ? BigDecimal.ZERO
                : values.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(values.size()),
                        scale, RoundingMode.HALF_UP);
    }

    /** the tags of every year, most expensive first */
    static List<String> tags(List<Year> years) {
        var totals = new LinkedHashMap<String, BigDecimal>();
        years.forEach(y -> y.getExpensesPerTag().forEach((t, v) -> totals.merge(t, v, BigDecimal::add)));
        return totals.entrySet().stream().sorted(Map.Entry.<String, BigDecimal> comparingByValue().reversed())
                .map(Map.Entry::getKey).toList();
    }
}
