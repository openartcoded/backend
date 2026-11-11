package tech.artcoded.websitev2.pages.businessplan;

import static tech.artcoded.websitev2.pages.businessplan.BusinessPlanTexts.text;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

import tech.artcoded.websitev2.pages.businessplan.BusinessPlanFigures.Scenario;
import tech.artcoded.websitev2.pages.businessplan.BusinessPlanFigures.Year;
import tech.artcoded.websitev2.pages.businessplan.BusinessPlanReport.ExceptionalRow;
import tech.artcoded.websitev2.pages.businessplan.BusinessPlanReport.ExpenseRow;
import tech.artcoded.websitev2.pages.businessplan.BusinessPlanReport.Kpi;
import tech.artcoded.websitev2.pages.businessplan.BusinessPlanReport.LoanView;
import tech.artcoded.websitev2.pages.businessplan.BusinessPlanReport.MonthRow;
import tech.artcoded.websitev2.pages.businessplan.BusinessPlanReport.Row;
import tech.artcoded.websitev2.pages.businessplan.BusinessPlanReport.YearRow;
import tech.artcoded.websitev2.pages.businessplan.BusinessPlanRequest.Language;
import tech.artcoded.websitev2.pages.dossier.report.BarChart;
import tech.artcoded.websitev2.pages.personal.PersonalInfo;

/** Builds the model of the template: every value formatted in the language of the plan. */
final class BusinessPlanView {
    static final String REVENUE_COLOR = "#2563eb";
    static final String RESULT_COLOR = "#16a34a";
    static final String PREVIOUS_COLOR = "#94a3b8";
    static final String OLDER_COLOR = "#cbd5e1";
    /** years compared month by month */
    static final int MONTH_YEARS = 3;

    private final Language lang;

    private BusinessPlanView(Language lang) {
        this.lang = lang;
    }

    static BusinessPlanReport report(BusinessPlanFigures figures, BusinessPlanRequest request, PersonalInfo org,
            String logo) {
        return new BusinessPlanView(request.languageOrDefault()).build(figures, request, org, logo);
    }

    private BusinessPlanReport build(BusinessPlanFigures f, BusinessPlanRequest request, PersonalInfo org,
            String logo) {
        var orgName = StringUtils.defaultIfBlank(org.getOrganizationName(), "-");
        var years = f.getYears();
        var complete = years.stream().filter(y -> !y.isPartial() && y.getRevenue().signum() > 0).toList();
        var last = complete.stream().findFirst().orElse(null);
        var current = years.stream().filter(Year::isPartial).findFirst().orElse(null);
        var since = years.stream().filter(y -> y.getRevenue().signum() > 0).map(Year::getYear)
                .min(Comparator.naturalOrder()).map(String::valueOf).orElse("-");
        var realistic = scenario(f, "REALISTIC");
        var monthName = monthName(f.getDate().getMonthValue(), TextStyle.FULL);

        var report = BusinessPlanReport.builder().lang(lang.name().toLowerCase()).t(BusinessPlanTexts.labels(lang))
                .title(text(lang, "title"))
                .subtitle(text(lang, "subtitle", "date",
                        f.getDate().format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale()))))
                .logo(StringUtils.defaultString(logo)).organizationName(orgName)
                .vatNumber(StringUtils.defaultString(org.getVatNumber())).address(address(org))
                .manager(StringUtils.defaultString(org.getCeoFullName()))
                .contact(Stream.of(org.getOrganizationEmailAddress(), org.getOrganizationPhoneNumber())
                        .filter(StringUtils::isNotBlank).collect(Collectors.joining(" · ")))
                .activity(StringUtils.defaultString(request.activity()).trim()).activeSince(since);

        // ---------- key figures ----------
        var summary = new ArrayList<String>();
        var kpis = new ArrayList<Kpi>();
        if (last != null) {
            summary.add(last.getGrowth() == null
                    ? text(lang, "summaryHistoryFirst", "org", orgName, "since", since, "year", last.getYear(),
                            "revenue", money(last.getRevenue()), "result", money(last.getResult()), "margin",
                            percent(last.getMargin()))
                    : text(lang, "summaryHistory", "org", orgName, "since", since, "year", last.getYear(), "revenue",
                            money(last.getRevenue()), "growth", signedPercent(last.getGrowth()), "previous",
                            last.getYear() - 1, "result", money(last.getResult()), "margin",
                            percent(last.getMargin())));
            kpis.add(new Kpi(text(lang, "kpiRevenue", "year", last.getYear()), money(last.getRevenue()),
                    last.getGrowth() == null ? ""
                            : text(lang, "vsPrevious", "growth", signedPercent(last.getGrowth()), "year",
                                    last.getYear() - 1),
                    tone(last.getGrowth())));
            kpis.add(new Kpi(text(lang, "kpiResult", "year", last.getYear()), money(last.getResult()),
                    text(lang, "marginOf", "margin", percent(last.getMargin())), tone(last.getResult())));
        }
        if (f.getHistoricalGrowth() != null && f.getBaseYears().size() > 1) {
            var span = yearsSpan(f);
            summary.add(text(lang, "summaryGrowth", "years", span, "growth", signedPercent(f.getHistoricalGrowth())));
            kpis.add(new Kpi(text(lang, "kpiGrowth"), signedPercent(f.getHistoricalGrowth()),
                    text(lang, "kpiGrowthDetail", "years", span), tone(f.getHistoricalGrowth())));
        }
        var ytdGrowth = growth(f.getYearToDate(), f.getYearToDatePrevious());
        if (current != null && f.getYearToDate().signum() > 0) {
            if (ytdGrowth != null) {
                summary.add(text(lang, "summaryYearToDate", "year", current.getYear(), "month", monthName, "ytd",
                        money(f.getYearToDate()), "growth", signedPercent(ytdGrowth), "previous",
                        current.getYear() - 1));
            }
            kpis.add(new Kpi(text(lang, "kpiYearToDate", "year", current.getYear()), money(f.getYearToDate()),
                    ytdGrowth == null ? ""
                            : text(lang, "vsPrevious", "growth", signedPercent(ytdGrowth), "year",
                                    current.getYear() - 1),
                    tone(ytdGrowth)));
        }
        if (realistic != null) {
            var next = f.getForecastYears().getFirst();
            summary.add(text(lang, "summaryForecast", "revenue", money(realistic.getRevenue().getFirst()), "year", next,
                    "result", money(realistic.getResult().getFirst())));
            kpis.add(new Kpi(text(lang, "kpiForecast", "year", next), money(realistic.getRevenue().getFirst()),
                    text(lang, "kpiForecastDetail", "result", money(realistic.getResult().getFirst())), "neutral"));
            if (f.getLoan() != null && !realistic.getCoverage().isEmpty()
                    && realistic.getCoverage().getFirst() != null) {
                summary.add(text(lang, "summaryLoan", "amount", money(f.getLoan().getAmount()), "payment",
                        money2(f.getLoan().getMonthlyPayment()), "year", next, "coverage",
                        decimal(realistic.getCoverage().getFirst())));
            }
        }
        report.summary(summary).kpis(kpis);

        // ---------- progress over the years ----------
        report.years(years.stream().map(this::yearRow).toList());
        report.yearsChart(yearsChart(f, complete, realistic));

        // ---------- progress during the year ----------
        var monthYears = years.stream().filter(y -> y.getRevenue().signum() > 0).limit(MONTH_YEARS)
                .sorted(Comparator.comparingInt(Year::getYear)).toList();
        report.monthYears(monthYears.stream().map(y -> String.valueOf(y.getYear())).toList());
        report.months(monthRows(monthYears, current, f.getDate().getMonthValue()));
        report.monthsChart(monthsChart(monthYears, current, f.getDate().getMonthValue()));
        report.monthsExplain(
                current != null && ytdGrowth != null
                        ? text(lang, "monthsExplain", "month", monthName, "year", current.getYear(), "ytd",
                                money(f.getYearToDate()), "previousYtd", money(f.getYearToDatePrevious()), "previous",
                                current.getYear() - 1, "growth", signedPercent(ytdGrowth))
                        : "");

        // ---------- expenses ----------
        var expenseYear = last != null ? last : current;
        report.expensesYear(expenseYear == null ? "" : String.valueOf(expenseYear.getYear()));
        report.expensesExplain(expenseYear == null ? "" : text(lang, "expensesExplain", "year", expenseYear.getYear()));
        report.expenses(expenseYear == null ? List.of() : expenseRows(expenseYear, complete));
        report.exceptionalExplain(text(lang, "exceptionalExplain", "threshold", money(f.getExceptionalThreshold())));
        report.exceptional(f.getExceptionalExpenses().stream()
                .map(e -> new ExceptionalRow(
                        e.date() == null ? "" : e.date().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                        StringUtils.defaultString(e.subject()), StringUtils.defaultIfBlank(e.tag(), "-"),
                        money(e.amount()), text(lang, e.reviewed() ? "confirmed" : "detected")))
                .toList());

        // ---------- projections ----------
        report.hasForecast(realistic != null);
        report.forecastYears(f.getForecastYears().stream().map(String::valueOf).toList());
        report.forecast(realistic == null ? List.of() : forecastRows(realistic, f.getLoan() != null));
        report.scenarios(f.getScenarios().stream()
                .map(s -> new Row(text(lang, s.getKey()), s.getResult().stream().map(this::money).toList(),
                        "REALISTIC".equals(s.getKey()), negatives(s.getResult())))
                .toList());
        report.exceptionalForecast(realistic == null ? ""
                : text(lang, "exceptionalForecast", "provision", money(realistic.getExceptionalProvision())));
        report.scenariosExplain(realistic == null ? ""
                : text(lang, "scenariosExplain", "prudentIndexation",
                        percent(pct(BusinessPlanCalculator.PRUDENT_INDEXATION_PERCENT)), "indexation",
                        percent(pct(BusinessPlanCalculator.INDEXATION_PERCENT)), "growth",
                        signedPercent(realistic.getGrowth()), "optimisticGrowth",
                        signedPercent(scenario(f, "OPTIMISTIC").getGrowth())));

        // ---------- financing ----------
        if (f.getLoan() != null) {
            var loan = f.getLoan();
            report.loan(new LoanView(money(loan.getAmount()), text(lang, "months", "n", loan.getMonths()),
                    decimal(loan.getRate()) + " %", money2(loan.getMonthlyPayment()), money2(loan.getTotalInterest()),
                    StringUtils.defaultString(request.loanPurpose()).trim()));
        }

        // ---------- assumptions ----------
        var method = new ArrayList<String>();
        method.add(text(lang, "methodAmounts"));
        if (last != null) {
            method.add(f.getHistoricalGrowth() == null ? text(lang, "methodBaseNoGrowth", "last", last.getYear())
                    : text(lang, "methodBase", "last", last.getYear(), "years", yearsSpan(f), "historical",
                            signedPercent(f.getHistoricalGrowth()), "cap",
                            percent(pct(BusinessPlanCalculator.MAX_GROWTH_PERCENT))));
            method.add(text(lang, "methodExpenses", "last", last.getYear(), "indexation",
                    percent(pct(BusinessPlanCalculator.INDEXATION_PERCENT)), "prudentIndexation",
                    percent(pct(BusinessPlanCalculator.PRUDENT_INDEXATION_PERCENT))));
            method.add(text(lang, "methodProvision"));
        }
        if (current != null) {
            method.add(text(lang, "methodPartial", "year", current.getYear()));
        }
        method.add(text(lang, "methodTax"));
        if (f.getLoan() != null) {
            method.add(text(lang, "methodLoan"));
        }
        report.method(method);
        return report.build();
    }

    // ---------- parts ----------

    private YearRow yearRow(Year y) {
        return new YearRow(
                y.isPartial() ? "%s (%s)".formatted(y.getYear(), text(lang, "soFar")) : String.valueOf(y.getYear()),
                y.isPartial(), money(y.getRevenue()), y.getGrowth() == null ? "-" : signedPercent(y.getGrowth()),
                tone(y.getGrowth()), money(y.getNormalExpenses()), money(y.getExceptionalExpenses()),
                money(y.getResult()), y.getMargin() == null ? "-" : percent(y.getMargin()), y.getResult().signum() < 0);
    }

    /** the complete years (oldest first) then the projection of the realistic scenario */
    private BarChart yearsChart(BusinessPlanFigures f, List<Year> complete, Scenario realistic) {
        var history = complete.stream().sorted(Comparator.comparingInt(Year::getYear)).toList();
        var categories = new ArrayList<String>();
        var revenue = new ArrayList<BigDecimal>();
        var result = new ArrayList<BigDecimal>();
        history.forEach(y -> {
            categories.add(String.valueOf(y.getYear()));
            revenue.add(y.getRevenue());
            result.add(y.getResult());
        });
        var projectedFrom = categories.size();
        if (realistic != null) {
            for (int i = 0; i < f.getForecastYears().size(); i++) {
                categories.add(String.valueOf(f.getForecastYears().get(i)));
                revenue.add(realistic.getRevenue().get(i));
                result.add(realistic.getResult().get(i));
            }
        }
        return BarChart.of(text(lang, "chartYears"), realistic == null ? "" : text(lang, "chartYearsSubtitle"),
                categories,
                List.of(new BarChart.Series(text(lang, "revenue"), REVENUE_COLOR, revenue),
                        new BarChart.Series(text(lang, "result"), RESULT_COLOR, result)),
                projectedFrom - 1, categories.size() <= 8, projectedFrom);
    }

    private BarChart monthsChart(List<Year> monthYears, Year current, int currentMonth) {
        var categories = IntStream.rangeClosed(1, 12).mapToObj(m -> monthName(m, TextStyle.SHORT)).toList();
        var series = new ArrayList<BarChart.Series>();
        for (int i = 0; i < monthYears.size(); i++) {
            var y = monthYears.get(i);
            var isCurrent = y == current;
            var values = new ArrayList<BigDecimal>();
            for (int m = 0; m < 12; m++) {
                values.add(isCurrent && m >= currentMonth ? null : y.getMonthlyRevenue().get(m));
            }
            var color = i == monthYears.size() - 1 ? REVENUE_COLOR
                    : i == monthYears.size() - 2 ? PREVIOUS_COLOR : OLDER_COLOR;
            series.add(new BarChart.Series(String.valueOf(y.getYear()), color, values));
        }
        return BarChart.of(text(lang, "chartMonths"), "", categories, series, current == null ? -1 : currentMonth - 1,
                false);
    }

    private List<MonthRow> monthRows(List<Year> monthYears, Year current, int currentMonth) {
        var rows = new ArrayList<MonthRow>();
        var cumulated = BigDecimal.ZERO;
        for (int m = 0; m < 12; m++) {
            var month = m;
            var values = monthYears.stream()
                    .map(y -> y == current && month >= currentMonth ? "" : money(y.getMonthlyRevenue().get(month)))
                    .toList();
            String cumulatedLabel = "";
            var cumulatedYear = current != null ? current : monthYears.isEmpty() ? null : monthYears.getLast();
            if (cumulatedYear != null && (cumulatedYear != current || m < currentMonth)) {
                cumulated = cumulated.add(cumulatedYear.getMonthlyRevenue().get(m));
                cumulatedLabel = money(cumulated);
            }
            rows.add(new MonthRow(monthName(m + 1, TextStyle.FULL), values, cumulatedLabel,
                    current != null && m == currentMonth - 1));
        }
        return rows;
    }

    private List<ExpenseRow> expenseRows(Year year, List<Year> complete) {
        var total = year.getNormalExpenses();
        var averageOf = complete.isEmpty() ? List.of(year) : complete;
        var max = year.getExpensesPerTag().values().stream().max(Comparator.naturalOrder()).orElse(BigDecimal.ONE);
        return BusinessPlanCalculator.tags(List.of(year)).stream().map(tag -> {
            var amount = year.getExpensesPerTag().getOrDefault(tag, BigDecimal.ZERO);
            var average = BusinessPlanCalculator.average(
                    averageOf.stream().map(y -> y.getExpensesPerTag().getOrDefault(tag, BigDecimal.ZERO)).toList(), 2);
            return new ExpenseRow(tag, money(amount),
                    total.signum() > 0 ? percent(amount.divide(total, 4, RoundingMode.HALF_UP)) : "-", money(average),
                    max.signum() > 0 ? amount.divide(max, 4, RoundingMode.HALF_UP).doubleValue() * 100 : 0);
        }).toList();
    }

    private List<Row> forecastRows(Scenario s, boolean withLoan) {
        var rows = new ArrayList<Row>();
        rows.add(row(text(lang, "revenue"), s.getRevenue(), this::money, false));
        rows.add(row(text(lang, "growth"), s.getRevenue().stream().map(r -> s.getGrowth()).toList(),
                this::signedPercent, false));
        rows.add(row(text(lang, "normalExpenses"), s.getNormalExpenses(), v -> "- " + money(v), false));
        rows.add(row(text(lang, "provision"), s.getRevenue().stream().map(r -> s.getExceptionalProvision()).toList(),
                v -> "- " + money(v), false));
        rows.add(new Row(text(lang, "result"), s.getResult().stream().map(this::money).toList(), true,
                negatives(s.getResult())));
        rows.add(row(text(lang, "margin"), s.getMargin(), v -> v == null ? "-" : percent(v), false));
        if (withLoan) {
            rows.add(row(text(lang, "loanPayments"), s.getDebtService(), v -> "- " + money(v), false));
            rows.add(row(text(lang, "coverage"), s.getCoverage(), v -> v == null ? "-" : decimal(v), true));
        }
        return rows;
    }

    private Row row(String label, List<BigDecimal> values, Function<BigDecimal, String> format, boolean strong) {
        return new Row(label, values.stream().map(format).toList(), strong, values.stream().map(v -> false).toList());
    }

    private static List<Boolean> negatives(List<BigDecimal> values) {
        return values.stream().map(v -> v != null && v.signum() < 0).toList();
    }

    private static Scenario scenario(BusinessPlanFigures f, String key) {
        return f.getScenarios().stream().filter(s -> s.getKey().equals(key)).findFirst().orElse(null);
    }

    private String yearsSpan(BusinessPlanFigures f) {
        var sorted = f.getBaseYears().stream().sorted().toList();
        return sorted.size() == 1 ? String.valueOf(sorted.getFirst()) : sorted.getFirst() + "–" + sorted.getLast();
    }

    private static String address(PersonalInfo org) {
        var city = (StringUtils.defaultString(org.getOrganizationPostCode()) + " "
                + StringUtils.defaultString(org.getOrganizationCity())).trim();
        return Stream.of(org.getOrganizationAddress(), city).filter(StringUtils::isNotBlank)
                .collect(Collectors.joining(", "));
    }

    private static BigDecimal growth(BigDecimal value, BigDecimal previous) {
        return previous == null || previous.signum() <= 0 ? null
                : value.divide(previous, 4, RoundingMode.HALF_UP).subtract(BigDecimal.ONE);
    }

    private static BigDecimal pct(int percent) {
        return BigDecimal.valueOf(percent, 2);
    }

    private static String tone(BigDecimal value) {
        return value == null || value.signum() == 0 ? "neutral" : value.signum() > 0 ? "good" : "bad";
    }

    // ---------- formatting ----------

    private Locale locale() {
        return switch (lang) {
            case FR -> Locale.forLanguageTag("fr-BE");
            case NL -> Locale.forLanguageTag("nl-BE");
            case EN -> Locale.forLanguageTag("en-GB");
        };
    }

    private String monthName(int month, TextStyle style) {
        var name = Month.of(month).getDisplayName(style, locale()).replace(".", "");
        return StringUtils.capitalize(name);
    }

    private DecimalFormat format(String pattern) {
        var symbols = new DecimalFormatSymbols(Locale.ROOT);
        switch (lang) {
            case EN -> {
                symbols.setGroupingSeparator(',');
                symbols.setDecimalSeparator('.');
            }
            case FR -> {
                symbols.setGroupingSeparator(' ');
                symbols.setDecimalSeparator(',');
            }
            case NL -> {
                symbols.setGroupingSeparator('.');
                symbols.setDecimalSeparator(',');
            }
        }
        symbols.setMinusSign('-');
        var format = new DecimalFormat(pattern, symbols);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format;
    }

    private String euro(String amount) {
        return switch (lang) {
            case FR -> amount + " €";
            case NL -> "€ " + amount;
            case EN -> "€" + amount;
        };
    }

    String money(BigDecimal value) {
        return euro(format("#,##0").format(value == null ? BigDecimal.ZERO : value));
    }

    String money2(BigDecimal value) {
        return euro(format("#,##0.00").format(value == null ? BigDecimal.ZERO : value));
    }

    String decimal(BigDecimal value) {
        return format("0.00").format(value == null ? BigDecimal.ZERO : value);
    }

    String percent(BigDecimal share) {
        if (share == null) {
            return "-";
        }
        var value = format("0.#").format(share.multiply(BigDecimal.valueOf(100)));
        return lang == Language.FR ? value + " %" : value + "%";
    }

    String signedPercent(BigDecimal share) {
        if (share == null) {
            return "-";
        }
        return (share.signum() > 0 ? "+" : "") + percent(share);
    }
}
