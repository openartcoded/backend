package tech.artcoded.websitev2.pages.invoice;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

public class InvoiceGenerationTest {

  @Test
  public void getTotal() {
    InvoiceRow row = InvoiceRow.builder()
      .amount(new BigDecimal("12"))
      .amountType(InvoicingType.DAYS)
      .rate(new BigDecimal("63.38"))
      .rateType(InvoicingType.HOURS)
      .build();
    Assertions.assertThat(row.getTotal()).isEqualTo(new BigDecimal("6084.48"));
    InvoiceGeneration invoice = InvoiceGeneration.builder()
      .invoiceTable(List.of(
        row
      )).build();
    Assertions.assertThat(invoice.getTaxes()).isEqualTo(new BigDecimal("1277.74"));
    Assertions.assertThat(invoice.getTotal()).isEqualTo(new BigDecimal("7362.22"));


  }

}
