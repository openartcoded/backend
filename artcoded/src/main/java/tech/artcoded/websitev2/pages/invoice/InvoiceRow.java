package tech.artcoded.websitev2.pages.invoice;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Transient;
import tech.artcoded.websitev2.domain.common.RateType;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class InvoiceRow implements Serializable {
    private static final long serialVersionUID = 1L;

    private String projectName;
    @Builder.Default
    private String period = InvoiceRow.generatePeriod();
    private String nature;
    private BigDecimal amount;
    private BigDecimal rate;
    @Builder.Default
    private BigDecimal hoursPerDay = new BigDecimal("8.0");
    private RateType amountType;
    private RateType rateType;

    /** Precision used for the intermediate division when a daily rate is applied to hours. */
    private static final MathContext INTERMEDIATE_PRECISION = MathContext.DECIMAL128;

    /**
     * Total of the row, rounded down to the cent. The amount and the rate can each be expressed in hours or in days;
     * the division by {@link #hoursPerDay} is only done when strictly needed (daily rate applied to hours) and never
     * throws, even when the result is not a terminating decimal (e.g. 7.5 hours per day).
     */
    @Transient
    public BigDecimal getTotal() {
        var total = switch (rateType) {
            case HOURS -> switch (amountType) {
                case HOURS -> rate.multiply(amount);
                case DAYS -> rate.multiply(amount).multiply(hoursPerDay);
            };
            case DAYS -> switch (amountType) {
                case DAYS -> rate.multiply(amount);
                case HOURS -> rate.multiply(amount).divide(hoursPerDay, INTERMEDIATE_PRECISION);
            };
        };
        return total.setScale(2, RoundingMode.DOWN);
    }

    public static String generatePeriod() {
        return DateTimeFormatter.ofPattern("MM/yyyy").format(LocalDate.now().minusMonths(1));
    }
}
