package tech.artcoded.websitev2.pages.fee;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Finds the expenses far above the usual ones (taxes, a one-off purchase): the price (VAT included) at least
 * {@code factor} times the median price of the expenses, and at least {@code minAmount}. The ones reviewed keep their
 * status (confirmed or denied) whatever their price.
 */
public final class ExceptionalExpenses {
    public static final BigDecimal DEFAULT_FACTOR = new BigDecimal("5");
    public static final BigDecimal DEFAULT_MIN_AMOUNT = new BigDecimal("1000");

    private ExceptionalExpenses() {
    }

    /**
     * @param median
     *            median price (VAT included) of the expenses with a price
     * @param threshold
     *            from this price, an expense is suspect
     * @param expenses
     *            the suspects and the reviewed ones, most expensive first
     */
    public record Report(BigDecimal median, BigDecimal threshold, BigDecimal factor, BigDecimal minAmount,
            List<Fee> expenses) implements Serializable {
    }

    public static BigDecimal median(Collection<Fee> fees) {
        var prices = fees.stream().map(Fee::getPriceTot).filter(p -> p.signum() > 0).sorted().toList();
        if (prices.isEmpty()) {
            return BigDecimal.ZERO;
        }
        var middle = prices.size() / 2;
        return prices.size() % 2 == 1 ? prices.get(middle)
                : prices.get(middle - 1).add(prices.get(middle)).divide(BigDecimal.TWO, 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal threshold(BigDecimal median, BigDecimal factor, BigDecimal minAmount) {
        return median.multiply(factor).max(minAmount);
    }

    /** big enough to be suspect, whatever its status */
    public static boolean isAboveThreshold(Fee fee, BigDecimal threshold) {
        return fee.getPriceTot().compareTo(threshold) >= 0;
    }

    /**
     * Exceptional for the figures: confirmed, or not reviewed and above the threshold. A denied one is a normal
     * expense.
     */
    public static boolean isExceptional(Fee fee, BigDecimal threshold) {
        if (fee.getExceptional() != null) {
            return fee.getExceptional() == ExceptionalStatus.CONFIRMED;
        }
        return isAboveThreshold(fee, threshold);
    }

    public static Report report(Collection<Fee> fees, BigDecimal factor, BigDecimal minAmount) {
        var median = median(fees);
        var threshold = threshold(median, factor, minAmount);
        var expenses = new ArrayList<>(fees.stream()
                .filter(f -> f.getExceptional() != null || isAboveThreshold(f, threshold))
                .sorted(Comparator.comparing(Fee::getPriceTot).reversed()).toList());
        return new Report(median, threshold, factor, minAmount, expenses);
    }
}
