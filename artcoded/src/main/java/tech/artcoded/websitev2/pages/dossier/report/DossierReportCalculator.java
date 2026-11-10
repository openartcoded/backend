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
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.IntStream;

import org.apache.commons.lang3.StringUtils;

import tech.artcoded.websitev2.pages.dossier.Dossier;
import tech.artcoded.websitev2.pages.dossier.report.DossierReport.DossierFigures;
import tech.artcoded.websitev2.pages.dossier.report.DossierReport.Insight;
import tech.artcoded.websitev2.pages.dossier.report.DossierReport.Kpi;
import tech.artcoded.websitev2.pages.dossier.report.DossierReport.Pending;
import tech.artcoded.websitev2.pages.dossier.report.DossierReport.ShareRow;
import tech.artcoded.websitev2.pages.dossier.report.DossierReport.Tone;
import tech.artcoded.websitev2.pages.dossier.report.DossierReport.YearRow;
import tech.artcoded.websitev2.pages.fee.Fee;
import tech.artcoded.websitev2.pages.invoice.InvoiceGeneration;
import tech.artcoded.websitev2.utils.helper.DateHelper;

/**
 * Computes the dossier report of a month from the processed invoices and expenses (the ones in a dossier), compared
 * with the same month and the same part of the year (january until the month) of the previous years.
 * <p>
 * Amounts are excluding VAT (invoice sub total, expense price excluding VAT); a credit note counts negatively. What is
 * dated after the month of the report is ignored: a report generated later for the same month gives the same figures.
 */
public final class DossierReportCalculator {
    /** years shown in the charts and the history, the year of the report included */
    static final int MAX_YEARS = 8;
    static final int MAX_TAGS = 8;
    static final int MAX_CLIENTS = 6;
    /** a client above this share of the revenue of the year is flagged as a dependency */
    static final BigDecimal CLIENT_DEPENDENCY_SHARE = BigDecimal.valueOf(60);

    static final String REVENUE_COLOR = "#2563eb";
    static final String EXPENSES_COLOR = "#f97316";
    static final String PREVIOUS_COLOR = "#94a3b8";
    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * @param processedInvoices
     *            the invoices of every dossier (logically deleted ones excluded)
     * @param processedExpenses
     *            the expenses of every dossier
     * @param pendingInvoices
     *            the invoices not in a dossier yet
     * @param pendingExpenses
     *            the expenses not in a dossier yet
     */
    public record Input(Dossier dossier, YearMonth month, Collection<InvoiceGeneration> processedInvoices,
            Collection<Fee> processedExpenses, Collection<InvoiceGeneration> pendingInvoices,
            Collection<Fee> pendingExpenses, LocalDate today) {
    }

    /** an amount at a date */
    record Entry(LocalDate date, BigDecimal amount, String group) {
    }

    /** the figures of a year until the month of the report */
    record YearFigures(int year, BigDecimal monthRevenue, BigDecimal monthExpenses, BigDecimal ytdRevenue,
            BigDecimal ytdExpenses, BigDecimal yearRevenue, BigDecimal yearExpenses, long ytdInvoices,
            long monthInvoices, long yearInvoices) {
        BigDecimal ytdMargin() {
            return ytdRevenue.subtract(ytdExpenses);
        }
    }

    private DossierReportCalculator() {
    }

    public static DossierReport compute(Input input) {
        var month = input.month();
        var end = month.atEndOfMonth();
        var revenue = input.processedInvoices().stream().filter(Objects::nonNull)
                .map(i -> new Entry(invoiceDate(i), revenue(i), clientName(i)))
                .filter(e -> e.date() != null && !e.date().isAfter(end)).toList();
        var expenses = input.processedExpenses().stream().filter(Objects::nonNull)
                .map(f -> new Entry(expenseDate(f), zeroIfNull(f.getPriceHVAT()), tag(f)))
                .filter(e -> e.date() != null && !e.date().isAfter(end)).toList();

        var years = years(month, revenue, expenses);
        var figures = years.stream().map(y -> yearFigures(y, month.getMonth(), revenue, expenses)).toList();
        var current = figures.getLast();
        var previous = figures.size() > 1 ? figures.get(figures.size() - 2) : emptyYear(month.getYear() - 1);

        var expensesPerTag = shareRows(expenses, month, MAX_TAGS, "Other tags", false);
        var topClients = shareRows(revenue, month, MAX_CLIENTS, "Other clients", true);
        var dossier = dossierFigures(input);
        var pending = pending(input);

        return DossierReport.builder().monthLabel(monthLabel(month)).monthName(monthName(month.getMonth()))
                .year(month.getYear()).previousYear(month.getYear() - 1)
                .ytdLabel(month.getMonth() == Month.JANUARY ? monthName(Month.JANUARY)
                        : monthName(Month.JANUARY) + " – " + monthName(month.getMonth()))
                .generatedDate(input.today().format(DATE)).organizationName("").organizationVatNumber("").logo("")
                .kpis(kpis(month, current, previous, pending)).insights(insights(month, figures, expensesPerTag,
                        topClients, dossier, pending, current, previous))
                .sameMonthChart(sameMonthChart(month, figures)).ytdChart(ytdChart(month, figures))
                .monthlyChart(monthlyChart(month, revenue)).expensesPerTag(expensesPerTag).topClients(topClients)
                .history(history(figures)).dossier(dossier).pending(pending).build();
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

    /** from the first year with data (at most MAX_YEARS ago) until the year of the report */
    static List<Integer> years(YearMonth month, List<Entry> revenue, List<Entry> expenses) {
        var first = java.util.stream.Stream.concat(revenue.stream(), expenses.stream()).map(e -> e.date().getYear())
                .min(Integer::compare).orElse(month.getYear());
        var from = Math.max(first, month.getYear() - MAX_YEARS + 1);
        return IntStream.rangeClosed(Math.min(from, month.getYear()), month.getYear()).boxed().toList();
    }

    static YearFigures yearFigures(int year, Month month, List<Entry> revenue, List<Entry> expenses) {
        var yearRevenue = revenue.stream().filter(e -> e.date().getYear() == year).toList();
        var yearExpenses = expenses.stream().filter(e -> e.date().getYear() == year).toList();
        var ytdRevenue = yearRevenue.stream().filter(e -> e.date().getMonthValue() <= month.getValue()).toList();
        var monthRevenue = yearRevenue.stream().filter(e -> e.date().getMonth() == month).toList();
        return new YearFigures(year, sum(monthRevenue),
                sum(yearExpenses.stream().filter(e -> e.date().getMonth() == month).toList()), sum(ytdRevenue),
                sum(yearExpenses.stream().filter(e -> e.date().getMonthValue() <= month.getValue()).toList()),
                sum(yearRevenue), sum(yearExpenses), ytdRevenue.size(), monthRevenue.size(), yearRevenue.size());
    }

    static YearFigures emptyYear(int year) {
        return new YearFigures(year, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, 0);
    }

    static BigDecimal sum(Collection<Entry> entries) {
        return entries.stream().map(Entry::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static String tone(BigDecimal current, BigDecimal previous, boolean higherIsBetter) {
        var evolution = evolution(current, previous);
        if (evolution == null || evolution.signum() == 0) {
            return Tone.NEUTRAL.getCss();
        }
        return (evolution.signum() > 0) == higherIsBetter ? Tone.GOOD.getCss() : Tone.BAD.getCss();
    }

    // ---------- kpis ----------

    static List<Kpi> kpis(YearMonth month, YearFigures current, YearFigures previous, Pending pending) {
        var kpis = new ArrayList<Kpi>();
        var previousMonth = monthLabel(month.minusYears(1));
        kpis.add(Kpi.builder().label("Revenue " + monthLabel(month)).value(money(current.monthRevenue()))
                .detail("vs %s in %s".formatted(money(previous.monthRevenue()), previousMonth))
                .evolution(evolutionLabel(current.monthRevenue(), previous.monthRevenue()))
                .tone(tone(current.monthRevenue(), previous.monthRevenue(), true)).build());
        kpis.add(Kpi.builder().label("Revenue year to date").value(money(current.ytdRevenue()))
                .detail("vs %s in %s".formatted(money(previous.ytdRevenue()), previous.year()))
                .evolution(evolutionLabel(current.ytdRevenue(), previous.ytdRevenue()))
                .tone(tone(current.ytdRevenue(), previous.ytdRevenue(), true)).build());
        kpis.add(Kpi.builder().label("Expenses year to date").value(money(current.ytdExpenses()))
                .detail("vs %s in %s".formatted(money(previous.ytdExpenses()), previous.year()))
                .evolution(evolutionLabel(current.ytdExpenses(), previous.ytdExpenses()))
                .tone(tone(current.ytdExpenses(), previous.ytdExpenses(), false)).build());
        var marginRate = share(current.ytdMargin(), current.ytdRevenue());
        kpis.add(Kpi.builder().label("Result year to date").value(money(current.ytdMargin()))
                .detail("%s of the revenue (%s in %s)".formatted(percent(marginRate),
                        percent(share(previous.ytdMargin(), previous.ytdRevenue())), previous.year()))
                .evolution(evolutionLabel(current.ytdMargin(), previous.ytdMargin()))
                .tone(tone(current.ytdMargin(), previous.ytdMargin(), true)).build());
        var projection = projection(month, current);
        kpis.add(Kpi.builder().label("Projected revenue " + month.getYear()).value(money(projection))
                .detail("at this pace (%s a month), vs %s in %s".formatted(
                        money(current.ytdRevenue().divide(BigDecimal.valueOf(month.getMonthValue()), 2,
                                RoundingMode.HALF_UP)),
                        money(previous.yearRevenue()), previous.year()))
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

    /** the revenue of the year if the monthly average so far goes on */
    static BigDecimal projection(YearMonth month, YearFigures current) {
        return current.ytdRevenue().multiply(BigDecimal.valueOf(12)).divide(BigDecimal.valueOf(month.getMonthValue()),
                2, RoundingMode.HALF_UP);
    }

    // ---------- insights ----------

    static List<Insight> insights(YearMonth month, List<YearFigures> figures, List<ShareRow> tags,
            List<ShareRow> clients, DossierFigures dossier, Pending pending, YearFigures current,
            YearFigures previous) {
        var insights = new ArrayList<Insight>();
        var monthLabel = monthLabel(month);
        var name = monthName(month.getMonth());

        // the month
        if (current.monthInvoices() == 0) {
            insights.add(new Insight(Tone.BAD.getCss(),
                    "No processed invoice is dated %s: invoice it or process it in the dossier.".formatted(monthLabel)));
        } else {
            var evolution = evolution(current.monthRevenue(), previous.monthRevenue());
            if (evolution != null) {
                insights.add(new Insight(tone(current.monthRevenue(), previous.monthRevenue(), true),
                        "Revenue of %s: %s, %s %s than in %s %s (%s).".formatted(monthLabel,
                                money(current.monthRevenue()), percent(evolution.abs()),
                                evolution.signum() >= 0 ? "more" : "less", name, previous.year(),
                                money(previous.monthRevenue()))));
            }
            var pastMonths = figures.subList(0, figures.size() - 1).stream()
                    .filter(f -> f.monthRevenue().signum() != 0).toList();
            if (pastMonths.size() >= 2) {
                if (pastMonths.stream().allMatch(f -> current.monthRevenue().compareTo(f.monthRevenue()) > 0)) {
                    insights.add(new Insight(Tone.GOOD.getCss(), "Best %s of the last %s years (since %s)."
                            .formatted(name, figures.size(), figures.getFirst().year())));
                } else if (pastMonths.stream().allMatch(f -> current.monthRevenue().compareTo(f.monthRevenue()) < 0)) {
                    insights.add(new Insight(Tone.BAD.getCss(), "Lowest %s of the last %s years (since %s)."
                            .formatted(name, figures.size(), figures.getFirst().year())));
                }
            }
        }

        // the year so far
        var revenueEvolution = evolution(current.ytdRevenue(), previous.ytdRevenue());
        var expensesEvolution = evolution(current.ytdExpenses(), previous.ytdExpenses());
        if (revenueEvolution != null) {
            insights.add(new Insight(tone(current.ytdRevenue(), previous.ytdRevenue(), true),
                    "Revenue from January to %s: %s, %s%s compared to %s.".formatted(name,
                            money(current.ytdRevenue()), revenueEvolution.signum() >= 0 ? "+" : "",
                            percent(revenueEvolution), previous.year())));
        }
        if (revenueEvolution != null && expensesEvolution != null) {
            if (expensesEvolution.subtract(revenueEvolution).compareTo(BigDecimal.valueOf(5)) > 0) {
                insights.add(new Insight(Tone.BAD.getCss(),
                        "Expenses grow faster than revenue this year (%s%s vs %s%s).".formatted(
                                expensesEvolution.signum() >= 0 ? "+" : "", percent(expensesEvolution),
                                revenueEvolution.signum() >= 0 ? "+" : "", percent(revenueEvolution))));
            } else if (revenueEvolution.subtract(expensesEvolution).compareTo(BigDecimal.valueOf(5)) > 0) {
                insights.add(new Insight(Tone.GOOD.getCss(),
                        "Revenue grows faster than expenses this year (%s%s vs %s%s).".formatted(
                                revenueEvolution.signum() >= 0 ? "+" : "", percent(revenueEvolution),
                                expensesEvolution.signum() >= 0 ? "+" : "", percent(expensesEvolution))));
            }
        }
        if (current.ytdRevenue().signum() > 0) {
            var rate = share(current.ytdMargin(), current.ytdRevenue());
            var previousRate = share(previous.ytdMargin(), previous.ytdRevenue());
            insights.add(new Insight(
                    previous.ytdRevenue().signum() > 0 ? tone(rate, previousRate, true) : Tone.NEUTRAL.getCss(),
                    "Result so far: %s of the revenue is left after expenses%s.".formatted(percent(rate),
                            previous.ytdRevenue().signum() > 0
                                    ? " (%s at the same time in %s)".formatted(percent(previousRate), previous.year())
                                    : "")));
        }

        // expenses
        tags.stream().filter(t -> !t.getName().equals("Other tags")).findFirst()
                .ifPresent(top -> insights.add(new Insight(Tone.NEUTRAL.getCss(),
                        "Biggest expense this year: %s with %s (%s of the expenses, %s vs %s).".formatted(
                                top.getName(), top.getCurrent(), top.getShare(), top.getEvolution(),
                                previous.year()))));

        // clients
        clients.stream().findFirst().filter(c -> !c.getName().equals("Other clients")).ifPresent(top -> {
            if (BigDecimal.valueOf(top.getShareValue()).compareTo(CLIENT_DEPENDENCY_SHARE) >= 0) {
                insights.add(new Insight(Tone.BAD.getCss(),
                        "%s brings %s of the revenue this year: the company depends on a single client."
                                .formatted(top.getName(), top.getShare())));
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
            insights.add(new Insight(dossier.getVatBalanceTone(), "%s %s: %s (due %s, advance payments %s)."
                    .formatted(dossier.getVatBalanceLabel(), dossier.getName(), dossier.getVatBalance(),
                            dossier.getVatDue(), dossier.getAdvancePayments())));
        }
        return insights;
    }

    // ---------- charts ----------

    static BarChart sameMonthChart(YearMonth month, List<YearFigures> figures) {
        return BarChart.of("%s over the years".formatted(monthName(month.getMonth())),
                "Revenue and expenses of %s, excluding VAT".formatted(monthName(month.getMonth())),
                figures.stream().map(f -> String.valueOf(f.year())).toList(),
                List.of(new BarChart.Series("Revenue", REVENUE_COLOR,
                        figures.stream().map(YearFigures::monthRevenue).toList()),
                        new BarChart.Series("Expenses", EXPENSES_COLOR,
                                figures.stream().map(YearFigures::monthExpenses).toList())),
                figures.size() - 1, true);
    }

    static BarChart ytdChart(YearMonth month, List<YearFigures> figures) {
        return BarChart.of("Year to date over the years",
                "Revenue and expenses from January to %s, excluding VAT".formatted(monthName(month.getMonth())),
                figures.stream().map(f -> String.valueOf(f.year())).toList(),
                List.of(new BarChart.Series("Revenue", REVENUE_COLOR,
                        figures.stream().map(YearFigures::ytdRevenue).toList()),
                        new BarChart.Series("Expenses", EXPENSES_COLOR,
                                figures.stream().map(YearFigures::ytdExpenses).toList())),
                figures.size() - 1, true);
    }

    /** revenue month by month, the year of the report (until its month) against the previous one */
    static BarChart monthlyChart(YearMonth month, List<Entry> revenue) {
        Function<Integer, List<BigDecimal>> perMonth = year -> IntStream.rangeClosed(1, 12)
                .mapToObj(m -> year == month.getYear() && m > month.getMonthValue() ? null
                        : sum(revenue.stream()
                                .filter(e -> e.date().getYear() == year && e.date().getMonthValue() == m).toList()))
                .toList();
        return BarChart.of("Revenue month by month",
                "%s compared to %s, excluding VAT".formatted(month.getYear(), month.getYear() - 1),
                IntStream.rangeClosed(1, 12).mapToObj(m -> shortMonthName(Month.of(m))).toList(),
                List.of(new BarChart.Series(String.valueOf(month.getYear() - 1), PREVIOUS_COLOR,
                        perMonth.apply(month.getYear() - 1)),
                        new BarChart.Series(String.valueOf(month.getYear()), REVENUE_COLOR,
                                perMonth.apply(month.getYear()))),
                month.getMonthValue() - 1, false);
    }

    // ---------- tables ----------

    /**
     * Year to date per group (tag or client), the biggest first, the rest grouped in one row.
     *
     * @param higherIsBetter
     *            revenue (true) or expenses (false), for the tone of the evolution
     */
    static List<ShareRow> shareRows(List<Entry> entries, YearMonth month, int max, String othersName,
            boolean higherIsBetter) {
        Function<Integer, Map<String, BigDecimal>> perGroup = year -> {
            var totals = new TreeMap<String, BigDecimal>();
            entries.stream().filter(e -> e.date().getYear() == year && e.date().getMonthValue() <= month.getMonthValue())
                    .forEach(e -> totals.merge(e.group(), e.amount(), BigDecimal::add));
            return totals;
        };
        var current = perGroup.apply(month.getYear());
        var previous = perGroup.apply(month.getYear() - 1);
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
        var biggest = rows.values().stream().flatMap(v -> java.util.stream.Stream.of(v[0], v[1]))
                .max(Comparator.naturalOrder()).filter(b -> b.signum() > 0).orElse(BigDecimal.ONE);
        return rows.entrySet().stream().map(e -> {
            var amounts = e.getValue();
            return ShareRow.builder().name(e.getKey()).current(money(amounts[0])).previous(money(amounts[1]))
                    .evolution(evolutionLabel(amounts[0], amounts[1]))
                    .evolutionTone(tone(amounts[0], amounts[1], higherIsBetter)).share(percent(share(amounts[0], total)))
                    .shareValue(share(amounts[0], total).doubleValue())
                    .currentBar(bar(amounts[0], biggest)).previousBar(bar(amounts[1], biggest)).build();
        }).toList();
    }

    private static double bar(BigDecimal amount, BigDecimal biggest) {
        return Math.max(0, Math.round(amount.doubleValue() / biggest.doubleValue() * 1000) / 10.0);
    }

    /** the most recent year first */
    static List<YearRow> history(List<YearFigures> figures) {
        var rows = new ArrayList<YearRow>();
        for (var i = figures.size() - 1; i >= 0; i--) {
            var f = figures.get(i);
            var partial = i == figures.size() - 1;
            var revenue = partial ? f.ytdRevenue() : f.yearRevenue();
            var expenses = partial ? f.ytdExpenses() : f.yearExpenses();
            // the year of the report is compared with the same period of the previous year
            var previousRevenue = i == 0 ? null
                    : partial ? figures.get(i - 1).ytdRevenue() : figures.get(i - 1).yearRevenue();
            rows.add(YearRow.builder().year(f.year()).partial(partial).revenue(money(revenue)).expenses(money(expenses))
                    .margin(money(revenue.subtract(expenses)))
                    .marginRate(percent(share(revenue.subtract(expenses), revenue)))
                    .monthRevenue(money(f.monthRevenue())).invoices(partial ? f.ytdInvoices() : f.yearInvoices())
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
        var invoiceIds = Optional.ofNullable(dossier.getInvoiceIds()).orElseGet(java.util.Set::of);
        var feeIds = Optional.ofNullable(dossier.getFeeIds()).orElseGet(java.util.Set::of);
        var invoices = input.processedInvoices().stream().filter(i -> invoiceIds.contains(i.getId())).toList();
        var expenses = input.processedExpenses().stream().filter(f -> feeIds.contains(f.getId())).toList();
        var revenue = invoices.stream().map(DossierReportCalculator::revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        var expensesTotal = expenses.stream().map(f -> zeroIfNull(f.getPriceHVAT())).reduce(BigDecimal.ZERO,
                BigDecimal::add);
        var dates = invoices.stream().map(DossierReportCalculator::invoiceDate).filter(Objects::nonNull).sorted()
                .toList();

        var builder = DossierFigures.builder().name(StringUtils.defaultString(dossier.getName()))
                .description(StringUtils.defaultString(dossier.getDescription())).closed(dossier.isClosed())
                .status(dossier.isClosed()
                        ? "Closed" + Optional.ofNullable(dossier.getClosedDate()).map(DateHelper::toLocalDate)
                                .map(d -> " on " + d.format(DATE)).orElse("")
                        : "Open")
                .period(dates.isEmpty() ? ""
                        : "%s – %s".formatted(dates.getFirst().format(DATE), dates.getLast().format(DATE)))
                .invoices(invoices.size()).revenue(money(revenue)).expenses(expenses.size())
                .expensesTotal(money(expensesTotal))
                .documents(Optional.ofNullable(dossier.getDocumentIds()).map(java.util.Set::size).orElse(0))
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
