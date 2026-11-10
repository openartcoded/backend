package tech.artcoded.websitev2.pages.dossier.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * The formatting of the report, done in java so that the jinja (v2) and the FreeMarker (bundled v1) templates show the
 * same values: amounts "12.345,67 €", compact axis labels "12,3k", percentages "+12,3 %".
 */
public final class ReportFormat {

    private ReportFormat() {
    }

    private static DecimalFormat decimalFormat(String pattern) {
        var symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        symbols.setMinusSign('-');
        return new DecimalFormat(pattern, symbols);
    }

    public static String money(BigDecimal amount) {
        return decimalFormat("#,##0.00").format(zeroIfNull(amount).setScale(2, RoundingMode.HALF_UP)) + " €";
    }

    /** short label for a chart: 950, 12,3k, 125k, 1,2M */
    public static String compact(BigDecimal amount) {
        var value = zeroIfNull(amount).doubleValue();
        var abs = Math.abs(value);
        if (abs >= 1_000_000) {
            return decimalFormat("0.#").format(value / 1_000_000) + "M";
        }
        if (abs >= 100_000) {
            return decimalFormat("0").format(value / 1_000) + "k";
        }
        if (abs >= 1_000) {
            return decimalFormat("0.#").format(value / 1_000) + "k";
        }
        return decimalFormat("0").format(value);
    }

    /** "12,3 %" of a ratio given in percent */
    public static String percent(BigDecimal percent) {
        return decimalFormat("0.#").format(zeroIfNull(percent).setScale(1, RoundingMode.HALF_UP)) + " %";
    }

    /**
     * Evolution from previous to current, in percent. Empty when there is nothing to compare with (previous is zero).
     */
    public static BigDecimal evolution(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.signum() == 0) {
            return null;
        }
        return zeroIfNull(current).subtract(previous).multiply(BigDecimal.valueOf(100)).divide(previous.abs(), 1,
                RoundingMode.HALF_UP);
    }

    /** "+12,3 %", "-4 %", "new" (nothing before), "–" (nothing at all) */
    public static String evolutionLabel(BigDecimal current, BigDecimal previous) {
        var evolution = evolution(current, previous);
        if (evolution == null) {
            return zeroIfNull(current).signum() == 0 ? "–" : "new";
        }
        return (evolution.signum() > 0 ? "+" : "") + percent(evolution);
    }

    /** share of part in total, in percent (0 when the total is 0) */
    public static BigDecimal share(BigDecimal part, BigDecimal total) {
        if (total == null || total.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return zeroIfNull(part).multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
    }

    public static String monthName(Month month) {
        return month.getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }

    public static String shortMonthName(Month month) {
        return month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
    }

    /** "October 2026" */
    public static String monthLabel(YearMonth month) {
        return monthName(month.getMonth()) + " " + month.getYear();
    }

    static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
