package tech.artcoded.websitev2.pages.invoice;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.artcoded.websitev2.domain.common.RateType;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class InvoiceSummary implements Serializable {
  private static final long serialVersionUID = 1L;

  private String period;
  private Date dateOfInvoice;
  private RateType amountType;
  private BigDecimal amount;
  private BigDecimal subTotal;
}
