package tech.artcoded.websitev2.pages.dossier.report;

import static tech.artcoded.websitev2.pages.dossier.report.ReportFormat.evolution;
import static tech.artcoded.websitev2.pages.dossier.report.ReportFormat.evolutionLabel;
import static tech.artcoded.websitev2.pages.dossier.report.ReportFormat.money;
import static tech.artcoded.websitev2.pages.dossier.report.ReportFormat.monthLabel;
import static tech.artcoded.websitev2.pages.dossier.report.ReportFormat.monthName;
import static tech.artcoded.websitev2.pages.dossier.report.ReportFormat.percent;
import static tech.artcoded.websitev2.pages.dossier.report.ReportFormat.share;
import static tech.artcoded.websitev2.pages.dossier.report.ReportFormat.shortMonthName;
import static tech.artcoded.websitev2.pages.dossier.report.ReportFormat.zeroIfNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

import tech.artcoded.websitev2.pages.dossier.Dossier;
import tech.artcoded.websitev2.pages.dossier.report.DossierReport.DossierFigures;
import tech.artcoded.websitev2.pages.dossier.report.DossierReport.Insight;
import tech.artcoded.websitev2.pages.dossier.report.DossierReport.Kpi;
import tech.artcoded.websitev2.pages.dossier.report.DossierReport.MonthRow;
import tech.artcoded.websitev2.pages.dossier.report.DossierReport.Pending;
import tech.artcoded.websitev2.pages.dossier.report.DossierReport.ShareRow;
import tech.artcoded.websitev2.pages.dossier.report.DossierReport.Tone;
import tech.artcoded.websitev2.pages.dossier.report.DossierReport.YearRow;
import tech.artcoded.websitev2.pages.fee.Fee;
import tech.artcoded.websitev2.pages.invoice.InvoiceGeneration;
import tech.artcoded.websitev2.utils.helper.DateHelper;

/**
 * Computes the report of a period of months (one month for the monthly report, every month of a dossier for the dossier
 * report) from the processed invoices and expenses (the ones in a dossier), compared with the same period of the
 * previous years.
 * <p>
 * Amounts are excluding VAT (invoice sub total, expense price excluding VAT); a credit note counts negatively. What is
 * dated after the period is ignored: a report generated later for the same period gives the same figures.
 */
public final class DossierReportCalculator {
    /** periods shown in the charts and the history, the one of the report included */
    static final int MAX_YEARS = 8;
    static final int MAX_TAGS = 8;
    static final int MAX_CLIENTS = 6;
    /** a client above this share of the revenue is flagged as a dependency */
    static final BigDecimal CLIENT_DEPENDENCY_SHARE = BigDecimal.valueOf(60);

    static final String REVENUE_COLOR = "#2563eb";
    static final String EXPENSES_COLOR = "#f97316";
    static final String PREVIOUS_COLOR = "#94a3b8";
    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * @param from
     *            first month of the period
     * @param to
     *            last month of the period (from for a single month)
     * @param processedInvoices
     *            the invoices of every dossier (logically deleted ones excluded)
     * @param processedExpenses
     *            the expenses of every dossier
     * @param pendingInvoices
     *            the invoices not in a dossier yet
     * @param pendingExpenses
     *            the expenses not in a dossier yet
     */
    public record Input(Dossier dossier, YearMonth from, YearMonth to, Collection<InvoiceGeneration> processedInvoices,
            Collection<Fee> processedExpenses, Collection<InvoiceGeneration> pendingInvoices,
            Collection<Fee> pendingExpenses, LocalDate today) {

        public Input {
            if (to.isBefore(from)) {
                throw new IllegalArgumentException("the period ends (%s) before it starts (%s)".formatted(to, from));
            }
        }

        /** the report of a single month */
        public Input(Dossier dossier, YearMonth month, Collection<InvoiceGeneration> processedInvoices,
                Collection<Fee> processedExpenses, Collection<InvoiceGeneration> pendingInvoices,
                Collection<Fee> pendingExpenses, LocalDate today) {
            this(dossier, month, month, processedInvoices, processedExpenses, pendingInvoices, pendingExpenses, today);
        }
    }

    /** an amount at a date */
    record Entry(LocalDate date, BigDecimal amount, String group) {
    }

    /** a period of months, both included */
    public record Period(YearMonth from, YearMonth to) {
        boolean contains(LocalDate date) {
            var month = YearMonth.from(date);
            return !month.isBefore(from) && !month.isAfter(to);
        }

        Period minusYears(int years) {
            return new Period(from.minusYears(years), to.minusYears(years));
        }

        boolean singleMonth() {
            return from.equals(to);
        }

        List<YearMonth> months() {
            var months = new ArrayList<YearMonth>();
            for (var m = from; !m.isAfter(to); m = m.plusMonths(1)) {
                months.add(m);
            }
            return months;
        }

        /** "October 2026", "January – October 2026", "October 2025 – September 2026" */
        String label() {
            if (singleMonth()) {
                return monthLabel(to);
            }
            if (from.getYear() == to.getYear()) {
                return "%s – %s".formatted(monthName(from.getMonth()), monthLabel(to));
            }
            return "%s – %s".formatted(monthLabel(from), monthLabel(to));
        }

        /** the period without the year: "October", "January – October" */
        String name() {
            return singleMonth() ? monthName(to.getMonth())
                    : "%s – %s".formatted(monthName(from.getMonth()), monthName(to.getMonth()));
        }

        /** column / axis label: "2026", or "2025–26" for a period over two years */
        String yearLabel() {
            return from.getYear() == to.getYear() ? String.valueOf(to.getYear())
                    : "%s–%s".formatted(from.getYear(), String.valueOf(to.getYear()).substring(2));
        }

        /** january of the last year until the last month */
        Period yearToDate() {
            return new Period(YearMonth.of(to.getYear(), 1), to);
        }

        Period calendarYear() {
            return new Period(YearMonth.of(to.getYear(), 1), YearMonth.of(to.getYear(), 12));
        }
    }

    /** the figures of the period of the report shifted by {@code offset} years (0: the period of the report) */
    record YearFigures(int offset, Period period, BigDecimal periodRevenue, BigDecimal periodExpenses,
            long periodInvoices, BigDecimal ytdRevenue, BigDecimal ytdExpenses, long ytdInvoices,
            BigDecimal yearRevenue, BigDecimal yearExpenses, long yearInvoices) {
        BigDecimal ytdMargin() {
            return ytdRevenue.subtract(ytdExpenses);
        }

        int year() {
            return period.to().getYear();
        }
    }

    private DossierReportCalculator() {
    }

    public static DossierReport compute(Input input) {
        var period = new Period(input.from(), input.to());
        var end = period.to().atEndOfMonth();
        var revenue = input.processedInvoices().stream().filter(Objects::nonNull)
                .map(i -> new Entry(invoiceDate(i), revenue(i), clientName(i)))
                .filter(e -> e.date() != null && !e.date().isAfter(end)).toList();
        var expenses = input.processedExpenses().stream().filter(Objects::nonNull)
                .map(f -> new Entry(expenseDate(f), zeroIfNull(f.getPriceHVAT()), tag(f)))
                .filter(e -> e.date() != null && !e.date().isAfter(end)).toList();

        // from the oldest period to the period of the report
        var figures = offsets(period, revenue, expenses).stream()
                .map(offset -> yearFigures(offset, period.minusYears(offset), revenue, expenses)).toList();
        var current = figures.getLast();
        var previous = figures.size() > 1 ? figures.get(figures.size() - 2) : emptyYear(1, period.minusYears(1));

        // what the expenses per tag and the clients cover: the year so far for one month, the period otherwise
        var breakdown = period.singleMonth() ? period.yearToDate() : period;
        var expensesPerTag = shareRows(expenses, breakdown, MAX_TAGS, "Other tags", false);
        var topClients = shareRows(revenue, breakdown, MAX_CLIENTS, "Other clients", true);
        var dossier = dossierFigures(input);
        var pending = pending(input);
        var months = monthRows(period, revenue, expenses);
        var breakdownLabel = period.singleMonth() ? ytdLabel(period) : period.label();

        return DossierReport.builder().periodLabel(period.label()).periodName(period.name())
                .singleMonth(period.singleMonth()).monthLabel(period.label()).monthName(period.name())
                .year(period.to().getYear()).previousYear(period.to().getYear() - 1).ytdLabel(ytdLabel(period))
                .breakdownLabel(breakdownLabel).currentColumn(period.yearLabel())
                .previousColumn(period.minusYears(1).yearLabel()).generatedDate(input.today().format(DATE))
                .organizationName("").organizationVatNumber("").logo("").kpis(kpis(period, current, previous, pending))
                .insights(insights(period, figures, months, expensesPerTag, topClients, dossier, pending, current,
                        previous))
                .sameMonthChart(periodChart(period, figures)).ytdChart(ytdChart(period, figures))
                .monthlyChart(monthlyChart(period, revenue)).months(months).expensesPerTag(expensesPerTag)
                .topClients(topClients).history(history(figures)).dossier(dossier).pending(pending).build();
    }

    // ---------- data ----------

    static LocalDate invoiceDate(InvoiceGeneration invoice) {
        return Optional.ofNullable(invoice.getDateOfInvoice()).or(() -> Optional.ofNullable(invoice.getDateCreation()))
                .map(DateHelper::toLocalDate).orElse(null);
    }

    static LocalDate expenseDate(Fee fee) {
        return Optional.ofNullable(fee.getDate()).or(() -> Optional.ofNullable(fee.getDateCreation()))
                .map(DateHelper::toLocalDate).orElse(null);
    }

    /** sub total (excluding VAT), negative for a credit note. An invoice that cannot be computed counts as 0 */
    static BigDecimal revenue(InvoiceGeneration invoice) {
        try {
            return zeroIfNull(invoice.getSubTotal());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    static String clientName(InvoiceGeneration invoice) {
        return Optional.ofNullable(invoice.getBillTo()).map(b -> b.getClientName()).filter(StringUtils::isNotBlank)
                .orElse("Unknown client");
    }

    static String tag(Fee fee) {
        return StringUtils.defaultIfBlank(fee.getTag(), "No tag");
    }

    static String ytdLabel(Period period) {
        var month = period.to().getMonth();
        return month == Month.JANUARY ? monthName(Month.JANUARY) : monthName(Month.JANUARY) + " – " + monthName(month);
    }

    /**
     * Year offsets of the periods compared, the oldest first: back to the first year with data, at most MAX_YEARS
     * periods (the one of the report included).
     */
    static List<Integer> offsets(Period period, List<Entry> revenue, List<Entry> expenses) {
        var first = Stream.concat(revenue.stream(), expenses.stream()).map(e -> e.date().getYear())
                .min(Integer::compare).orElse(period.to().getYear());
        var max = Math.min(MAX_YEARS - 1, Math.max(0, period.to().getYear() - first));
        return IntStream.rangeClosed(0, max).map(i -> max - i).boxed().toList();
    }

    static BigDecimal sum(List<Entry> entries, Period period) {
        return entries.stream().filter(e -> period.contains(e.date())).map(Entry::amount).reduce(BigDecimal.ZERO,
                BigDecimal::add);
    }

    static long count(List<Entry> entries, Period period) {
        return entries.stream().filter(e -> period.contains(e.date())).count();
    }

    static YearFigures yearFigures(int offset, Period period, List<Entry> revenue, List<Entry> expenses) {
        var ytd = period.yearToDate();
        var year = period.calendarYear();
        return new YearFigures(offset, period, sum(revenue, period), sum(expenses, period), count(revenue, period),
                sum(revenue, ytd), sum(expenses, ytd), count(revenue, ytd), sum(revenue, year), sum(expenses, year),
                count(revenue, year));
    }

    static YearFigures emptyYear(int offset, Period period) {
        return new YearFigures(offset, period, BigDecimal.ZERO, BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO, 0,
                BigDecimal.ZERO, BigDecimal.ZERO, 0);
    }

    static String tone(BigDecimal current, BigDecimal previous, boolean higherIsBetter) {
        var evolution = evolution(current, previous);
        if (evolution == null || evolution.signum() == 0) {
            return Tone.NEUTRAL.getCss();
        }
        return (evolution.signum() > 0) == higherIsBetter ? Tone.GOOD.getCss() : Tone.BAD.getCss();
    }

    private static String signed(BigDecimal evolution) {
        return (evolution.signum() >= 0 ? "+" : "") + percent(evolution);
    }

    // ---------- kpis ----------

    static List<Kpi> kpis(Period period, YearFigures current, YearFigures previous, Pending pending) {
        var kpis = new ArrayList<Kpi>();
        kpis.add(Kpi.builder().label("Revenue " + period.label()).value(money(current.periodRevenue()))
                .detail("vs %s in %s".formatted(money(previous.periodRevenue()), previous.period().label()))
                .evolution(evolutionLabel(current.periodRevenue(), previous.periodRevenue()))
                .tone(tone(current.periodRevenue(), previous.periodRevenue(), true)).build());
        if (!period.singleMonth()) {
            var result = current.periodRevenue().subtract(current.periodExpenses());
            var previousResult = previous.periodRevenue().subtract(previous.periodExpenses());
            kpis.add(Kpi.builder().label("Expenses " + period.label()).value(money(current.periodExpenses()))
                    .detail("vs %s in %s".formatted(money(previous.periodExpenses()), previous.period().label()))
                    .evolution(evolutionLabel(current.periodExpenses(), previous.periodExpenses()))
                    .tone(tone(current.periodExpenses(), previous.periodExpenses(), false)).build());
            kpis.add(Kpi.builder().label("Result " + period.label()).value(money(result))
                    .detail("%s of the revenue (%s in %s)".formatted(percent(share(result, current.periodRevenue())),
                            percent(share(previousResult, previous.periodRevenue())), previous.period().label()))
                    .evolution(evolutionLabel(result, previousResult)).tone(tone(result, previousResult, true))
                    .build());
        }
        kpis.add(Kpi.builder().label("Revenue year to date").value(money(current.ytdRevenue()))
                .detail("vs %s in %s".formatted(money(previous.ytdRevenue()), current.year() - 1))
                .evolution(evolutionLabel(current.ytdRevenue(), previous.ytdRevenue()))
                .tone(tone(current.ytdRevenue(), previous.ytdRevenue(), true)).build());
        if (period.singleMonth()) {
            kpis.add(Kpi.builder().label("Expenses year to date").value(money(current.ytdExpenses()))
                    .detail("vs %s in %s".formatted(money(previous.ytdExpenses()), current.year() - 1))
                    .evolution(evolutionLabel(current.ytdExpenses(), previous.ytdExpenses()))
                    .tone(tone(current.ytdExpenses(), previous.ytdExpenses(), false)).build());
            var marginRate = share(current.ytdMargin(), current.ytdRevenue());
            kpis.add(Kpi.builder().label("Result year to date").value(money(current.ytdMargin()))
                    .detail("%s of the revenue (%s in %s)".formatted(percent(marginRate),
                            percent(share(previous.ytdMargin(), previous.ytdRevenue())), current.year() - 1))
                    .evolution(evolutionLabel(current.ytdMargin(), previous.ytdMargin()))
                    .tone(tone(current.ytdMargin(), previous.ytdMargin(), true)).build());
        }
        var month = period.to();
        var projection = projection(month, current);
        kpis.add(Kpi.builder().label("Projected revenue " + month.getYear()).value(money(projection))
                .detail("at the pace of %s (%s a month), vs %s in %s".formatted(month.getYear(),
                        money(current.ytdRevenue().divide(BigDecimal.valueOf(month.getMonthValue()), 2,
                                RoundingMode.HALF_UP)),
                        money(previous.yearRevenue()), month.getYear() - 1))
                .evolution(evolutionLabel(projection, previous.yearRevenue()))
                .tone(tone(projection, previous.yearRevenue(), true)).build());
        kpis.add(Kpi.builder().label("Not processed yet")
                .value("%s invoice(s), %s expense(s)".formatted(pending.getInvoices(), pending.getExpenses()))
                .detail("%s invoiced, %s of expenses".formatted(pending.getInvoicesTotal(), pending.getExpensesTotal()))
                .evolution("")
                .tone(pending.getInvoices() + pending.getExpenses() > 0 ? Tone.BAD.getCss() : Tone.GOOD.getCss())
                .build());
        return kpis;
    }

    /** the revenue of the year of the last month if the monthly average of that year goes on */
    static BigDecimal projection(YearMonth month, YearFigures current) {
        return current.ytdRevenue().multiply(BigDecimal.valueOf(12)).divide(BigDecimal.valueOf(month.getMonthValue()),
                2, RoundingMode.HALF_UP);
    }

    // ---------- insights ----------

    static List<Insight> insights(Period period, List<YearFigures> figures, List<MonthRow> months, List<ShareRow> tags,
            List<ShareRow> clients, DossierFigures dossier, Pending pending, YearFigures current,
            YearFigures previous) {
        var insights = new ArrayList<Insight>();
        var label = period.label();
        var scope = period.singleMonth() ? "this year" : "over the period";

        // the period
        if (current.periodInvoices() == 0) {
            insights.add(new Insight(Tone.BAD.getCss(),
                    "No processed invoice is dated %s: invoice it or process it in the dossier.".formatted(label)));
        } else {
            var withoutInvoice = months.stream().filter(m -> m.getInvoices() == 0).map(MonthRow::getLabel).toList();
            if (!period.singleMonth() && !withoutInvoice.isEmpty()) {
                insights.add(new Insight(Tone.BAD.getCss(),
                        "No processed invoice in %s.".formatted(String.join(", ", withoutInvoice))));
            }
            var evolution = evolution(current.periodRevenue(), previous.periodRevenue());
            if (evolution != null) {
                insights.add(new Insight(tone(current.periodRevenue(), previous.periodRevenue(), true),
                        "Revenue of %s: %s, %s %s than in %s (%s).".formatted(label, money(current.periodRevenue()),
                                percent(evolution.abs()), evolution.signum() >= 0 ? "more" : "less",
                                previous.period().label(), money(previous.periodRevenue()))));
            }
            var pastPeriods = figures.subList(0, figures.size() - 1).stream()
                    .filter(f -> f.periodRevenue().signum() != 0).toList();
            if (pastPeriods.size() >= 2) {
                var since = figures.getFirst().period().yearLabel();
                if (pastPeriods.stream().allMatch(f -> current.periodRevenue().compareTo(f.periodRevenue()) > 0)) {
                    insights.add(new Insight(Tone.GOOD.getCss(), "Best %s of the last %s years (since %s)."
                            .formatted(period.name(), figures.size(), since)));
                } else if (pastPeriods.stream()
                        .allMatch(f -> current.periodRevenue().compareTo(f.periodRevenue()) < 0)) {
                    insights.add(new Insight(Tone.BAD.getCss(), "Lowest %s of the last %s years (since %s)."
                            .formatted(period.name(), figures.size(), since)));
                }
            }
            if (!period.singleMonth()) {
                months.stream().filter(m -> m.getInvoices() > 0).max(Comparator.comparing(MonthRow::getRevenueValue))
                        .ifPresent(best -> insights
                                .add(new Insight(Tone.NEUTRAL.getCss(), "Best month of the period: %s with %s."
                                        .formatted(best.getLabel(), best.getRevenue()))));
            }
        }

        // the year so far (one month) or the period, revenue against expenses
        var revenueEvolution = period.singleMonth() ? evolution(current.ytdRevenue(), previous.ytdRevenue())
                : evolution(current.periodRevenue(), previous.periodRevenue());
        var expensesEvolution = period.singleMonth() ? evolution(current.ytdExpenses(), previous.ytdExpenses())
                : evolution(current.periodExpenses(), previous.periodExpenses());
        if (period.singleMonth() && revenueEvolution != null) {
            insights.add(new Insight(tone(current.ytdRevenue(), previous.ytdRevenue(), true),
                    "Revenue from January to %s: %s, %s compared to %s.".formatted(monthName(period.to().getMonth()),
                            money(current.ytdRevenue()), signed(revenueEvolution), current.year() - 1)));
        }
        if (revenueEvolution != null && expensesEvolution != null) {
            if (expensesEvolution.subtract(revenueEvolution).compareTo(BigDecimal.valueOf(5)) > 0) {
                insights.add(new Insight(Tone.BAD.getCss(), "Expenses grow faster than revenue %s (%s vs %s)."
                        .formatted(scope, signed(expensesEvolution), signed(revenueEvolution))));
            } else if (revenueEvolution.subtract(expensesEvolution).compareTo(BigDecimal.valueOf(5)) > 0) {
                insights.add(new Insight(Tone.GOOD.getCss(), "Revenue grows faster than expenses %s (%s vs %s)."
                        .formatted(scope, signed(revenueEvolution), signed(expensesEvolution))));
            }
        }
        var revenue = period.singleMonth() ? current.ytdRevenue() : current.periodRevenue();
        var margin = period.singleMonth() ? current.ytdMargin()
                : current.periodRevenue().subtract(current.periodExpenses());
        var previousRevenue = period.singleMonth() ? previous.ytdRevenue() : previous.periodRevenue();
        var previousMargin = period.singleMonth() ? previous.ytdMargin()
                : previous.periodRevenue().subtract(previous.periodExpenses());
        if (revenue.signum() > 0) {
            var rate = share(margin, revenue);
            var previousRate = share(previousMargin, previousRevenue);
            insights.add(
                    new Insight(previousRevenue.signum() > 0 ? tone(rate, previousRate, true) : Tone.NEUTRAL.getCss(),
                            "Result %s: %s of the revenue is left after expenses%s.".formatted(
                                    period.singleMonth() ? "so far" : "of the period", percent(rate),
                                    previousRevenue.signum() > 0 ? " (%s %s)".formatted(percent(previousRate),
                                            period.singleMonth()
                                                    ? "at the same time in %s".formatted(current.year() - 1)
                                                    : "in " + previous.period().label())
                                            : "")));
        }

        // expenses
        tags.stream().filter(t -> !t.getName().equals("Other tags")).findFirst()
                .ifPresent(top -> insights.add(new Insight(Tone.NEUTRAL.getCss(),
                        "Biggest expense %s: %s with %s (%s of the expenses, %s vs a year before).".formatted(scope,
                                top.getName(), top.getCurrent(), top.getShare(), top.getEvolution()))));

        // clients
        clients.stream().findFirst().filter(c -> !c.getName().equals("Other clients")).ifPresent(top -> {
            if (BigDecimal.valueOf(top.getShareValue()).compareTo(CLIENT_DEPENDENCY_SHARE) >= 0) {
                insights.add(new Insight(Tone.BAD.getCss(),
                        "%s brings %s of the revenue %s: the company depends on a single client."
                                .formatted(top.getName(), top.getShare(), scope)));
            }
        });

        // what's left to do
        if (pending.getInvoices() > 0) {
            insights.add(new Insight(Tone.BAD.getCss(), "%s invoice(s) for %s are not processed in a dossier yet."
                    .formatted(pending.getInvoices(), pending.getInvoicesTotal())));
        }
        if (pending.getExpenses() > 0) {
            insights.add(new Insight(Tone.BAD.getCss(), "%s expense(s) for %s are not processed in a dossier yet."
                    .formatted(pending.getExpenses(), pending.getExpensesTotal())));
        }
        if (dossier.isVatKnown()) {
            insights.add(new Insight(dossier.getVatBalanceTone(),
                    "%s %s: %s (due %s, advance payments %s).".formatted(dossier.getVatBalanceLabel(),
                            dossier.getName(), dossier.getVatBalance(), dossier.getVatDue(),
                            dossier.getAdvancePayments())));
        }
        return insights;
    }

    // ---------- charts ----------

    /** the period of the report against the same period of the previous years */
    static BarChart periodChart(Period period, List<YearFigures> figures) {
        return BarChart.of("%s over the years".formatted(period.name()),
                "Revenue and expenses of %s, excluding VAT".formatted(period.name()),
                figures.stream().map(f -> f.period().yearLabel()).toList(),
                List.of(new BarChart.Series("Revenue", REVENUE_COLOR,
                        figures.stream().map(YearFigures::periodRevenue).toList()),
                        new BarChart.Series("Expenses", EXPENSES_COLOR,
                                figures.stream().map(YearFigures::periodExpenses).toList())),
                figures.size() - 1, true);
    }

    static BarChart ytdChart(Period period, List<YearFigures> figures) {
        return BarChart.of("Year to date over the years",
                "Revenue and expenses from January to %s, excluding VAT".formatted(monthName(period.to().getMonth())),
                figures.stream().map(f -> String.valueOf(f.year())).toList(),
                List.of(new BarChart.Series("Revenue", REVENUE_COLOR,
                        figures.stream().map(YearFigures::ytdRevenue).toList()),
                        new BarChart.Series("Expenses", EXPENSES_COLOR,
                                figures.stream().map(YearFigures::ytdExpenses).toList())),
                figures.size() - 1, true);
    }

    /**
     * Revenue month by month against the same month a year before: january to december of the year of a single month
     * (until the month), every month of a longer period.
     */
    static BarChart monthlyChart(Period period, List<Entry> revenue) {
        Function<YearMonth, BigDecimal> perMonth = m -> sum(revenue, new Period(m, m));
        if (period.singleMonth()) {
            var month = period.to();
            var year = month.getYear();
            return BarChart.of("Revenue month by month", "%s compared to %s, excluding VAT".formatted(year, year - 1),
                    IntStream.rangeClosed(1, 12).mapToObj(m -> shortMonthName(Month.of(m))).toList(), List.of(
                            new BarChart.Series(String.valueOf(year - 1), PREVIOUS_COLOR,
                                    IntStream.rangeClosed(1, 12)
                                            .mapToObj(m -> perMonth.apply(YearMonth.of(year - 1, m))).toList()),
                            new BarChart.Series(String.valueOf(year), REVENUE_COLOR,
                                    IntStream.rangeClosed(1, 12)
                                            .mapToObj(m -> m > month.getMonthValue() ? null
                                                    : perMonth.apply(YearMonth.of(year, m)))
                                            .toList())),
                    month.getMonthValue() - 1, false);
        }
        var months = period.months();
        var spansYears = period.from().getYear() != period.to().getYear();
        var previous = period.minusYears(1);
        return BarChart.of("Revenue month by month",
                "%s compared to %s, excluding VAT".formatted(period.label(), previous.label()),
                months.stream()
                        .map(m -> shortMonthName(m.getMonth())
                                + (spansYears ? " " + String.valueOf(m.getYear()).substring(2) : ""))
                        .toList(),
                List.of(new BarChart.Series(previous.yearLabel(), PREVIOUS_COLOR,
                        months.stream().map(m -> perMonth.apply(m.minusYears(1))).toList()),
                        new BarChart.Series(period.yearLabel(), REVENUE_COLOR, months.stream().map(perMonth).toList())),
                -1, false);
    }

    // ---------- tables ----------

    /** every month of the period against the same month a year before */
    static List<MonthRow> monthRows(Period period, List<Entry> revenue, List<Entry> expenses) {
        var rows = period.months().stream().map(m -> {
            var month = new Period(m, m);
            var previousMonth = month.minusYears(1);
            return new BigDecimal[] { sum(revenue, month), sum(revenue, previousMonth), sum(expenses, month),
                    BigDecimal.valueOf(count(revenue, month)) };
        }).toList();
        var biggest = rows.stream().flatMap(r -> Stream.of(r[0], r[1])).max(Comparator.naturalOrder())
                .filter(b -> b.signum() > 0).orElse(BigDecimal.ONE);
        var months = period.months();
        return IntStream.range(0, months.size()).mapToObj(i -> {
            var r = rows.get(i);
            return MonthRow.builder().label(monthLabel(months.get(i))).revenue(money(r[0])).revenueValue(r[0])
                    .previousRevenue(money(r[1])).evolution(evolutionLabel(r[0], r[1]))
                    .evolutionTone(tone(r[0], r[1], true)).expenses(money(r[2])).result(money(r[0].subtract(r[2])))
                    .invoices(r[3].longValue()).revenueBar(bar(r[0], biggest)).previousBar(bar(r[1], biggest)).build();
        }).toList();
    }

    /**
     * Per group (tag or client) over a period against the same period a year before, the biggest first, the rest
     * grouped in one row.
     *
     * @param higherIsBetter
     *            revenue (true) or expenses (false), for the tone of the evolution
     */
    static List<ShareRow> shareRows(List<Entry> entries, Period period, int max, String othersName,
            boolean higherIsBetter) {
        Function<Period, Map<String, BigDecimal>> perGroup = p -> {
            var totals = new TreeMap<String, BigDecimal>();
            entries.stream().filter(e -> p.contains(e.date()))
                    .forEach(e -> totals.merge(e.group(), e.amount(), BigDecimal::add));
            return totals;
        };
        var current = perGroup.apply(period);
        var previous = perGroup.apply(period.minusYears(1));
        var total = current.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        var sorted = new ArrayList<>(current.entrySet().stream().filter(e -> e.getValue().signum() != 0)
                .sorted(Map.Entry.<String, BigDecimal> comparingByValue().reversed()).toList());
        var rows = new LinkedHashMap<String, BigDecimal[]>();
        for (var i = 0; i < sorted.size(); i++) {
            var entry = sorted.get(i);
            var previousAmount = previous.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            if (i < max - 1 || sorted.size() == max) {
                rows.put(entry.getKey(), new BigDecimal[] { entry.getValue(), previousAmount });
            } else {
                rows.merge(othersName, new BigDecimal[] { entry.getValue(), previousAmount },
                        (a, b) -> new BigDecimal[] { a[0].add(b[0]), a[1].add(b[1]) });
            }
        }
        var biggest = rows.values().stream().flatMap(v -> Stream.of(v[0], v[1])).max(Comparator.naturalOrder())
                .filter(b -> b.signum() > 0).orElse(BigDecimal.ONE);
        return rows.entrySet().stream().map(e -> {
            var amounts = e.getValue();
            return ShareRow.builder().name(e.getKey()).current(money(amounts[0])).previous(money(amounts[1]))
                    .evolution(evolutionLabel(amounts[0], amounts[1]))
                    .evolutionTone(tone(amounts[0], amounts[1], higherIsBetter))
                    .share(percent(share(amounts[0], total))).shareValue(share(amounts[0], total).doubleValue())
                    .currentBar(bar(amounts[0], biggest)).previousBar(bar(amounts[1], biggest)).build();
        }).toList();
    }

    private static double bar(BigDecimal amount, BigDecimal biggest) {
        return Math.max(0, Math.round(amount.doubleValue() / biggest.doubleValue() * 1000) / 10.0);
    }

    /** calendar years, the most recent first; the year of the report until its last month only */
    static List<YearRow> history(List<YearFigures> figures) {
        var rows = new ArrayList<YearRow>();
        for (var i = figures.size() - 1; i >= 0; i--) {
            var f = figures.get(i);
            var partial = i == figures.size() - 1;
            var revenue = partial ? f.ytdRevenue() : f.yearRevenue();
            var expenses = partial ? f.ytdExpenses() : f.yearExpenses();
            // the year of the report is compared with the same part of the previous year
            var previousRevenue = i == 0 ? null
                    : partial ? figures.get(i - 1).ytdRevenue() : figures.get(i - 1).yearRevenue();
            rows.add(YearRow.builder().year(f.year()).partial(partial).revenue(money(revenue)).expenses(money(expenses))
                    .margin(money(revenue.subtract(expenses)))
                    .marginRate(percent(share(revenue.subtract(expenses), revenue)))
                    .monthRevenue(money(f.periodRevenue())).invoices(partial ? f.ytdInvoices() : f.yearInvoices())
                    .revenueEvolution(previousRevenue == null ? "–" : evolutionLabel(revenue, previousRevenue))
                    .revenueEvolutionTone(
                            previousRevenue == null ? Tone.NEUTRAL.getCss() : tone(revenue, previousRevenue, true))
                    .build());
        }
        return rows;
    }

    // ---------- the dossier ----------

    static DossierFigures dossierFigures(Input input) {
        var dossier = input.dossier();
        var invoiceIds = Optional.ofNullable(dossier.getInvoiceIds()).orElseGet(Set::of);
        var feeIds = Optional.ofNullable(dossier.getFeeIds()).orElseGet(Set::of);
        var invoices = input.processedInvoices().stream().filter(i -> invoiceIds.contains(i.getId())).toList();
        var expenses = input.processedExpenses().stream().filter(f -> feeIds.contains(f.getId())).toList();
        var revenue = invoices.stream().map(DossierReportCalculator::revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        var expensesTotal = expenses.stream().map(f -> zeroIfNull(f.getPriceHVAT())).reduce(BigDecimal.ZERO,
                BigDecimal::add);
        var dates = invoices.stream().map(DossierReportCalculator::invoiceDate).filter(Objects::nonNull).sorted()
                .toList();

        var builder = DossierFigures.builder().name(StringUtils.defaultString(dossier.getName()))
                .description(StringUtils.defaultString(dossier.getDescription())).closed(dossier.isClosed())
                .status(dossier.isClosed() ? "Closed" + Optional.ofNullable(dossier.getClosedDate())
                        .map(DateHelper::toLocalDate).map(d -> " on " + d.format(DATE)).orElse("") : "Open")
                .period(dates.isEmpty() ? ""
                        : "%s – %s".formatted(dates.getFirst().format(DATE), dates.getLast().format(DATE)))
                .invoices(invoices.size()).revenue(money(revenue)).expenses(expenses.size())
                .expensesTotal(money(expensesTotal))
                .documents(Optional.ofNullable(dossier.getDocumentIds()).map(Set::size).orElse(0))
                .result(money(revenue.subtract(expensesTotal)));

        if (dossier.getTvaDue() == null) {
            return builder.vatKnown(false).vatDue("").advancePayments(money(dossier.getTotalAdvancePayments()))
                    .vatBalanceLabel("").vatBalance("").vatBalanceTone(Tone.NEUTRAL.getCss()).build();
        }
        // advance payments - vat due: negative, there is still vat to pay
        var balance = dossier.getTvaToBePaid();
        return builder.vatKnown(true).vatDue(money(dossier.getTvaDue()))
                .advancePayments(money(dossier.getTotalAdvancePayments()))
                .vatBalanceLabel(balance.signum() < 0 ? "VAT still to pay for" : "VAT overpaid for")
                .vatBalance(money(balance.abs()))
                .vatBalanceTone(balance.signum() < 0 ? Tone.BAD.getCss() : Tone.GOOD.getCss()).build();
    }

    static Pending pending(Input input) {
        var invoicesTotal = input.pendingInvoices().stream().map(DossierReportCalculator::revenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var expensesTotal = input.pendingExpenses().stream().map(f -> zeroIfNull(f.getPriceHVAT()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return Pending.builder().invoices(input.pendingInvoices().size()).invoicesTotal(money(invoicesTotal))
                .expenses(input.pendingExpenses().size()).expensesTotal(money(expensesTotal)).build();
    }
}
