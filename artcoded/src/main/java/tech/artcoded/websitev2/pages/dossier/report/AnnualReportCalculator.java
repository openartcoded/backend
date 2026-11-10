package tech.artcoded.websitev2.pages.dossier.report;

import static tech.artcoded.websitev2.pages.dossier.report.DossierReportCalculator.EXPENSES_COLOR;
import static tech.artcoded.websitev2.pages.dossier.report.DossierReportCalculator.REVENUE_COLOR;
import static tech.artcoded.websitev2.pages.dossier.report.DossierReportCalculator.expenseDate;
import static tech.artcoded.websitev2.pages.dossier.report.DossierReportCalculator.invoiceDate;
import static tech.artcoded.websitev2.pages.dossier.report.DossierReportCalculator.tone;
import static tech.artcoded.websitev2.pages.dossier.report.ReportFormat.evolution;
import static tech.artcoded.websitev2.pages.dossier.report.ReportFormat.evolutionLabel;
import static tech.artcoded.websitev2.pages.dossier.report.ReportFormat.money;
import static tech.artcoded.websitev2.pages.dossier.report.ReportFormat.monthName;
import static tech.artcoded.websitev2.pages.dossier.report.ReportFormat.percent;
import static tech.artcoded.websitev2.pages.dossier.report.ReportFormat.share;
import static tech.artcoded.websitev2.pages.dossier.report.ReportFormat.zeroIfNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Month;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

import tech.artcoded.websitev2.pages.dossier.Dossier;
import tech.artcoded.websitev2.pages.dossier.report.DossierReport.Forecast;
import tech.artcoded.websitev2.pages.dossier.report.DossierReport.ForecastRow;
import tech.artcoded.websitev2.pages.dossier.report.DossierReport.Insight;
import tech.artcoded.websitev2.pages.dossier.report.DossierReport.Kpi;
import tech.artcoded.websitev2.pages.dossier.report.DossierReport.Tone;

/**
 * The report of a year: the report of the period of the year (January to December, to the current month for the year in
 * progress) over every dossier of the year, compared with every other year (rank, average), and the trend of the last
 * three years projected on the next ones.
 * <p>
 * The year in progress counts at its pace so far (its revenue until now x 12 / months) in the rank and the trend:
 * marked "estimated".
 */
public final class AnnualReportCalculator {
    /** years in the trend, the year of the report included */
    static final int TREND_YEARS = 3;
    /** years projected after the year of the report */
    static final int PROJECTED_YEARS = 3;
    /** years shown in the chart before the projected ones */
    static final int CHART_YEARS = 8;

    private AnnualReportCalculator() {
    }

    /** the figures of a whole year; estimated: the year isn't over, its total is its pace so far */
    record YearTotal(int year, BigDecimal revenue, BigDecimal expenses, boolean estimated) {
        BigDecimal result() {
            return revenue.subtract(expenses);
        }

        boolean hasData() {
            return revenue.signum() != 0 || expenses.signum() != 0;
        }
    }

    /** a straight line fitted on the years: value = intercept + slope * year */
    record Trend(double slope, double intercept) {
        BigDecimal at(int year) {
            return BigDecimal.valueOf(Math.max(0, intercept + slope * year)).setScale(2, RoundingMode.HALF_UP);
        }
    }

    /**
     * The dossiers of a year as one dossier: their invoices, expenses and documents, closed when all are; the VAT due
     * when every dossier has one, and the advance payments of all of them.
     */
    public static Dossier yearDossier(int year, List<Dossier> dossiers) {
        var allClosed = !dossiers.isEmpty() && dossiers.stream().allMatch(Dossier::isClosed);
        var vatKnown = !dossiers.isEmpty() && dossiers.stream().allMatch(d -> d.getTvaDue() != null);
        return Dossier.builder().id("year-" + year).name(String.valueOf(year))
                .description(dossiers.stream().map(Dossier::getName).filter(Objects::nonNull)
                        .collect(Collectors.joining(", ")))
                .invoiceIds(union(dossiers.stream().map(Dossier::getInvoiceIds)))
                .feeIds(union(dossiers.stream().map(Dossier::getFeeIds)))
                .documentIds(union(dossiers.stream().map(Dossier::getDocumentIds))).closed(allClosed)
                .closedDate(
                        allClosed
                                ? dossiers.stream().map(Dossier::getClosedDate).filter(Objects::nonNull)
                                        .max(Date::compareTo).orElse(null)
                                : null)
                .tvaDue(vatKnown ? dossiers.stream().map(Dossier::getTvaDue).reduce(BigDecimal.ZERO, BigDecimal::add)
                        : null)
                .advancePayments(dossiers.stream()
                        .flatMap(d -> Optional.ofNullable(d.getAdvancePayments()).orElseGet(List::of).stream())
                        .toList())
                .build();
    }

    private static Set<String> union(Stream<Set<String>> sets) {
        var union = new HashSet<String>();
        sets.filter(Objects::nonNull).forEach(union::addAll);
        return union;
    }

    /**
     * @param input
     *            the period of the year (from January), its dossier: {@link #yearDossier}
     * @param dossiers
     *            the dossiers of the year
     */
    public static DossierReport compute(DossierReportCalculator.Input input, List<Dossier> dossiers) {
        var base = DossierReportCalculator.compute(input);
        var year = input.to().getYear();
        var to = input.to();
        var partial = to.getMonth() != Month.DECEMBER;

        var totals = yearTotals(input, year, to);
        var current = totals.getLast();
        var previous = totals.subList(0, totals.size() - 1).stream().filter(YearTotal::hasData).toList();
        var forecast = forecast(totals, year, to);

        var dossierRows = dossiers.stream().sorted(Comparator.comparing(d -> StringUtils.defaultString(d.getName())))
                .map(d -> DossierReportCalculator.dossierFigures(new DossierReportCalculator.Input(d, input.from(),
                        input.to(), input.processedInvoices(), input.processedExpenses(), input.pendingInvoices(),
                        input.pendingExpenses(), input.today())))
                .toList();

        var kpis = new ArrayList<Kpi>(base.getKpis().stream()
                .filter(k -> k.getLabel().startsWith("Revenue " + base.getPeriodLabel())
                        || k.getLabel().startsWith("Expenses " + base.getPeriodLabel())
                        || k.getLabel().startsWith("Result " + base.getPeriodLabel()))
                .toList());
        kpis.add(rankKpi(current, previous));
        kpis.add(averageKpi(current, previous));
        forecast.map(f -> projectedKpi(f, year)).ifPresentOrElse(kpis::add, () -> base.getKpis().stream()
                .filter(k -> k.getLabel().startsWith("Not processed")).findFirst().ifPresent(kpis::add));

        var insights = new ArrayList<Insight>();
        if (partial) {
            insights.add(new Insight(Tone.NEUTRAL.getCss(),
                    "%s isn't over: its figures run until %s, the whole year is estimated at the pace so far (%s of revenue)."
                            .formatted(year, monthName(to.getMonth()), money(current.revenue()))));
        }
        insights.addAll(rankInsights(current, previous));
        insights.addAll(forecastInsights(totals, year, to));
        insights.addAll(base.getInsights());

        return base.toBuilder().annual(true).kind("Annual report").kpis(kpis).insights(insights).dossiers(dossierRows)
                .forecast(forecast.orElse(null)).build();
    }

    // ---------- the years ----------

    /**
     * Every year with data until the year of the report (included, estimated when it isn't over), the oldest first.
     */
    static List<YearTotal> yearTotals(DossierReportCalculator.Input input, int year, YearMonth to) {
        var revenue = new TreeMap<Integer, BigDecimal>();
        var expenses = new TreeMap<Integer, BigDecimal>();
        var end = to.atEndOfMonth();
        input.processedInvoices().stream().filter(Objects::nonNull).forEach(i -> {
            var date = invoiceDate(i);
            if (date != null && !date.isAfter(end)) {
                revenue.merge(date.getYear(), DossierReportCalculator.revenue(i), BigDecimal::add);
            }
        });
        input.processedExpenses().stream().filter(Objects::nonNull).forEach(f -> {
            var date = expenseDate(f);
            if (date != null && !date.isAfter(end)) {
                expenses.merge(date.getYear(), zeroIfNull(f.getPriceHVAT()), BigDecimal::add);
            }
        });
        var first = Stream.concat(revenue.keySet().stream(), expenses.keySet().stream()).min(Integer::compare)
                .orElse(year);
        var partial = to.getMonth() != Month.DECEMBER;
        var months = BigDecimal.valueOf(to.getMonthValue());
        return IntStream.rangeClosed(Math.min(first, year), year).mapToObj(y -> {
            var r = revenue.getOrDefault(y, BigDecimal.ZERO);
            var e = expenses.getOrDefault(y, BigDecimal.ZERO);
            if (y == year && partial) {
                // the pace so far over twelve months
                r = r.multiply(BigDecimal.valueOf(12)).divide(months, 2, RoundingMode.HALF_UP);
                e = e.multiply(BigDecimal.valueOf(12)).divide(months, 2, RoundingMode.HALF_UP);
            }
            return new YearTotal(y, r, e, y == year && partial);
        }).toList();
    }

    private static String label(YearTotal total) {
        return total.estimated() ? "%s (estimated)".formatted(total.year()) : String.valueOf(total.year());
    }

    private static String yearsLabel(List<YearTotal> years) {
        if (years.isEmpty()) {
            return "";
        }
        return years.size() == 1 ? String.valueOf(years.getFirst().year())
                : "%s–%s".formatted(years.getFirst().year(), years.getLast().year());
    }

    static BigDecimal average(List<YearTotal> years, java.util.function.Function<YearTotal, BigDecimal> value) {
        if (years.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return years.stream().map(value).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(years.size()), 2, RoundingMode.HALF_UP);
    }

    /** 1 for the best revenue */
    static int rank(YearTotal current, List<YearTotal> previous) {
        return 1 + (int) previous.stream().filter(p -> p.revenue().compareTo(current.revenue()) > 0).count();
    }

    static String ordinal(int n) {
        var suffix = (n % 100 >= 11 && n % 100 <= 13) ? "th" : switch (n % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
        return n + suffix;
    }

    static Kpi rankKpi(YearTotal current, List<YearTotal> previous) {
        var count = previous.size() + 1;
        var rank = rank(current, previous);
        var best = previous.stream().max(Comparator.comparing(YearTotal::revenue));
        return Kpi.builder().label("Rank among %s year%s".formatted(count, count > 1 ? "s" : ""))
                .value(count == 1 ? "First year" : rank == 1 ? "Best year" : "%s best".formatted(ordinal(rank)))
                .detail(best.filter(b -> rank > 1).map(b -> "best: %s with %s".formatted(b.year(), money(b.revenue())))
                        .orElse("revenue %s of %s".formatted(money(current.revenue()), label(current))))
                .evolution("")
                .tone(count == 1 ? Tone.NEUTRAL.getCss()
                        : rank == 1 ? Tone.GOOD.getCss() : rank == count ? Tone.BAD.getCss() : Tone.NEUTRAL.getCss())
                .build();
    }

    static Kpi averageKpi(YearTotal current, List<YearTotal> previous) {
        var average = average(previous, YearTotal::revenue);
        return Kpi.builder().label("Average revenue of the other years").value(money(average))
                .detail(previous.isEmpty() ? "no other year"
                        : "%s, vs %s for %s".formatted(yearsLabel(previous), money(current.revenue()), label(current)))
                .evolution(previous.isEmpty() ? "" : evolutionLabel(current.revenue(), average))
                .tone(previous.isEmpty() ? Tone.NEUTRAL.getCss() : tone(current.revenue(), average, true)).build();
    }

    static Kpi projectedKpi(Forecast forecast, int year) {
        var next = forecast.getRows().stream().filter(r -> r.getYear() == year + 1).findFirst().orElseThrow();
        return Kpi.builder().label("Projected revenue " + (year + 1)).value(next.getRevenue())
                .detail("trend of %s, result %s".formatted(forecast.getBasis(), next.getResult()))
                .evolution(forecast.getRevenueGrowth()).tone(forecast.getRevenueGrowthTone()).build();
    }

    static List<Insight> rankInsights(YearTotal current, List<YearTotal> previous) {
        var insights = new ArrayList<Insight>();
        if (previous.isEmpty()) {
            insights.add(new Insight(Tone.NEUTRAL.getCss(),
                    "%s is the first year with data: nothing to compare with yet.".formatted(current.year())));
            return insights;
        }
        var count = previous.size() + 1;
        var rank = rank(current, previous);
        var since = previous.getFirst().year();
        if (rank == 1) {
            insights.add(new Insight(Tone.GOOD.getCss(), "%s is the best year since %s (%s years): %s of revenue."
                    .formatted(label(current), since, count, money(current.revenue()))));
        } else if (rank == count) {
            insights.add(new Insight(Tone.BAD.getCss(), "%s is the lowest year since %s (%s years): %s of revenue."
                    .formatted(label(current), since, count, money(current.revenue()))));
        } else {
            var best = previous.stream().max(Comparator.comparing(YearTotal::revenue)).orElseThrow();
            insights.add(new Insight(Tone.NEUTRAL.getCss(), "%s is the %s best year of %s (best: %s with %s)."
                    .formatted(label(current), ordinal(rank), count, best.year(), money(best.revenue()))));
        }
        var average = average(previous, YearTotal::revenue);
        var evolution = evolution(current.revenue(), average);
        if (evolution != null) {
            insights.add(new Insight(tone(current.revenue(), average, true),
                    "Revenue %s %s the average of %s (%s).".formatted(percent(evolution.abs()),
                            evolution.signum() >= 0 ? "above" : "below", yearsLabel(previous), money(average))));
        }
        var averageResult = average(previous, YearTotal::result);
        var resultRate = share(current.result(), current.revenue());
        var averageRate = share(averageResult, average(previous, YearTotal::revenue));
        if (current.revenue().signum() > 0) {
            insights.add(new Insight(tone(resultRate, averageRate, true),
                    "Result %s: %s of the revenue (%s on average for %s).".formatted(money(current.result()),
                            percent(resultRate), percent(averageRate), yearsLabel(previous))));
        }
        return insights;
    }

    // ---------- the forecast ----------

    /** the line closest to the points (least squares); a single point: flat */
    static Trend fit(List<YearTotal> points, java.util.function.Function<YearTotal, BigDecimal> value) {
        var n = points.size();
        var meanX = points.stream().mapToDouble(YearTotal::year).average().orElse(0);
        var meanY = points.stream().mapToDouble(p -> value.apply(p).doubleValue()).average().orElse(0);
        var numerator = 0.0;
        var denominator = 0.0;
        for (var p : points) {
            numerator += (p.year() - meanX) * (value.apply(p).doubleValue() - meanY);
            denominator += (p.year() - meanX) * (p.year() - meanX);
        }
        var slope = n < 2 || denominator == 0 ? 0 : numerator / denominator;
        return new Trend(slope, meanY - slope * meanX);
    }

    /** average yearly growth from the first point to the last, in percent; null when the first is not positive */
    static BigDecimal growth(List<YearTotal> points, java.util.function.Function<YearTotal, BigDecimal> value) {
        var first = value.apply(points.getFirst()).doubleValue();
        var last = value.apply(points.getLast()).doubleValue();
        var years = points.getLast().year() - points.getFirst().year();
        if (first <= 0 || last < 0 || years <= 0) {
            return null;
        }
        return BigDecimal.valueOf((Math.pow(last / first, 1.0 / years) - 1) * 100).setScale(1, RoundingMode.HALF_UP);
    }

    private static String growthLabel(BigDecimal growth) {
        if (growth == null) {
            return "–";
        }
        return "%s%s a year".formatted(growth.signum() > 0 ? "+" : "", percent(growth));
    }

    private static String growthTone(BigDecimal growth, boolean higherIsBetter) {
        if (growth == null || growth.signum() == 0) {
            return Tone.NEUTRAL.getCss();
        }
        return (growth.signum() > 0) == higherIsBetter ? Tone.GOOD.getCss() : Tone.BAD.getCss();
    }

    private static String trendLabel(Trend trend) {
        var perYear = BigDecimal.valueOf(trend.slope()).setScale(2, RoundingMode.HALF_UP);
        return "%s%s a year".formatted(perYear.signum() > 0 ? "+" : "", money(perYear));
    }

    /** the last TREND_YEARS years with data, until the year of the report */
    static List<YearTotal> trendPoints(List<YearTotal> totals, int year) {
        var points = totals.stream().filter(t -> t.year() > year - TREND_YEARS).filter(YearTotal::hasData).toList();
        return points.size() >= 2 ? points : List.of();
    }

    static Optional<Forecast> forecast(List<YearTotal> totals, int year, YearMonth to) {
        var points = trendPoints(totals, year);
        if (points.isEmpty()) {
            return Optional.empty();
        }
        var revenueTrend = fit(points, YearTotal::revenue);
        var expensesTrend = fit(points, YearTotal::expenses);
        var revenueGrowth = growth(points, YearTotal::revenue);
        var expensesGrowth = growth(points, YearTotal::expenses);

        var rows = new ArrayList<ForecastRow>();
        points.forEach(p -> rows.add(row(p.year(), p.estimated() ? "estimated" : "actual", p.revenue(), p.expenses())));
        var projected = IntStream.rangeClosed(year + 1, year + PROJECTED_YEARS)
                .mapToObj(y -> new YearTotal(y, revenueTrend.at(y), expensesTrend.at(y), true)).toList();
        projected.forEach(p -> rows.add(row(p.year(), "projected", p.revenue(), p.expenses())));

        var shown = totals.subList(Math.max(0, totals.size() - CHART_YEARS), totals.size());
        var chartYears = Stream.concat(shown.stream(), projected.stream()).toList();
        var chart = BarChart.of("Revenue and expenses, then the projection",
                "Excluding VAT, the next %s years at the trend of %s".formatted(PROJECTED_YEARS, yearsLabel(points)),
                chartYears.stream()
                        .map(t -> t.estimated() && t.year() == year ? t.year() + "*" : String.valueOf(t.year()))
                        .toList(),
                List.of(new BarChart.Series("Revenue", REVENUE_COLOR,
                        chartYears.stream().map(YearTotal::revenue).toList()),
                        new BarChart.Series("Expenses", EXPENSES_COLOR,
                                chartYears.stream().map(YearTotal::expenses).toList())),
                shown.size() - 1, false, shown.size());

        var estimated = points.stream().anyMatch(YearTotal::estimated);
        var basis = yearsLabel(points)
                + (estimated ? " (%s estimated at the pace of January – %s)".formatted(year, monthName(to.getMonth()))
                        : "");
        return Optional.of(Forecast.builder().basis(basis).revenueGrowth(growthLabel(revenueGrowth))
                .revenueGrowthTone(growthTone(revenueGrowth, true)).revenueTrend(trendLabel(revenueTrend))
                .expensesGrowth(growthLabel(expensesGrowth)).expensesGrowthTone(growthTone(expensesGrowth, false))
                .expensesTrend(trendLabel(expensesTrend)).rows(rows).chart(chart)
                .method("A straight line through the revenue and the expenses of %s (least squares), extended to %s–%s. "
                        .formatted(yearsLabel(points), year + 1, year + PROJECTED_YEARS)
                        + "The growth a year goes from the first year to the last one. A trend, not a prediction: "
                        + "a new client or a lost one changes it.")
                .build());
    }

    private static ForecastRow row(int year, String status, BigDecimal revenue, BigDecimal expenses) {
        var result = revenue.subtract(expenses);
        return ForecastRow.builder().year(year).status(status).revenue(money(revenue)).expenses(money(expenses))
                .result(money(result)).marginRate(percent(share(result, revenue))).build();
    }

    static List<Insight> forecastInsights(List<YearTotal> totals, int year, YearMonth to) {
        var points = trendPoints(totals, year);
        if (points.isEmpty()) {
            return List.of(new Insight(Tone.NEUTRAL.getCss(),
                    "Not enough history for a projection: it needs at least two years with data."));
        }
        var insights = new ArrayList<Insight>();
        var revenueTrend = fit(points, YearTotal::revenue);
        var revenueGrowth = growth(points, YearTotal::revenue);
        var expensesGrowth = growth(points, YearTotal::expenses);
        if (revenueGrowth != null) {
            insights.add(new Insight(growthTone(revenueGrowth, true),
                    "Over %s revenue %s by about %s a year (%s): at this pace %s in %s, %s in %s.".formatted(
                            yearsLabel(points), revenueGrowth.signum() >= 0 ? "grows" : "shrinks",
                            percent(revenueGrowth.abs()), trendLabel(revenueTrend), money(revenueTrend.at(year + 1)),
                            year + 1, money(revenueTrend.at(year + PROJECTED_YEARS)), year + PROJECTED_YEARS)));
        }
        if (revenueGrowth != null && expensesGrowth != null
                && expensesGrowth.subtract(revenueGrowth).compareTo(BigDecimal.valueOf(2)) > 0) {
            var expensesTrend = fit(points, YearTotal::expenses);
            var last = year + PROJECTED_YEARS;
            var lastRevenue = revenueTrend.at(last);
            var lastRate = share(lastRevenue.subtract(expensesTrend.at(last)), lastRevenue);
            insights.add(new Insight(Tone.BAD.getCss(),
                    "Expenses grow faster than revenue over %s (%s vs %s a year): the result would be %s of the revenue in %s."
                            .formatted(yearsLabel(points), growthLabel(expensesGrowth), growthLabel(revenueGrowth),
                                    percent(lastRate), last)));
        }
        var firstRate = share(points.getFirst().result(), points.getFirst().revenue());
        var lastRate = share(points.getLast().result(), points.getLast().revenue());
        if (points.getFirst().revenue().signum() > 0 && points.getLast().revenue().signum() > 0
                && firstRate.subtract(lastRate).abs().compareTo(BigDecimal.ONE) >= 0) {
            insights.add(new Insight(tone(lastRate, firstRate, true),
                    "The result went from %s of the revenue in %s to %s in %s.".formatted(percent(firstRate),
                            points.getFirst().year(), percent(lastRate), label(points.getLast()))));
        }
        return insights;
    }
}
