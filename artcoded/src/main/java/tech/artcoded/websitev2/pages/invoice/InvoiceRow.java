package tech.artcoded.websitev2.pages.invoice;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Transient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class InvoiceRow {

  private String projectName;
  @Builder.Default
  private String period = InvoiceRow.generatePeriod();
  private String nature;
  private BigDecimal amount;
  private BigDecimal rate;
  @Builder.Default
  private BigDecimal hoursPerDay = new BigDecimal("8.0");
  private InvoicingType amountType;
  private InvoicingType rateType;

  @Transient
  public BigDecimal getTotal() {
    var ratePerHour = switch (rateType) {
      case DAYS -> rate.divide(hoursPerDay);
      case HOURS -> rate;
    };
    var amountPerhour = switch (amountType) {
      case HOURS -> amount;
      case DAYS -> amount.multiply(hoursPerDay);
    };
    return ratePerHour.multiply(amountPerhour).setScale(2, RoundingMode.DOWN);
  }

  public static String generatePeriod() {
    return DateTimeFormatter.ofPattern("MM/yyyy").format(LocalDate.now().minusMonths(1));
  }
}
