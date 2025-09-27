package tech.artcoded.websitev2.pages.invoice;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import tech.artcoded.websitev2.domain.common.RateType;
import tech.artcoded.websitev2.utils.helper.DateHelper;

import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public class InvoiceGenerationTest {

  @Test
  public void getTotal() {
    InvoiceRow row = InvoiceRow.builder()
        .amount(new BigDecimal("12"))
        .amountType(RateType.DAYS)
        .rate(new BigDecimal("63.38"))
        .rateType(RateType.HOURS)
        .build();
    Assertions.assertThat(row.getTotal()).isEqualTo(new BigDecimal("6084.48"));
    InvoiceGeneration invoice = InvoiceGeneration.builder()
        .invoiceTable(List.of(
            row))
        .build();
    Assertions.assertThat(invoice.getTaxes()).isEqualTo(new BigDecimal("1277.74"));
    Assertions.assertThat(invoice.getTotal()).isEqualTo(new BigDecimal("7362.22"));

  }

  @Test
  public void validateGenBankReference() {
    InvoiceGeneration invoice = InvoiceGeneration.builder()
        .seqInvoiceNumber(1L)
        .billTo(BillTo.builder().vatNumber("1234567890").build())
        .invoiceTable(List.of())
        .dateOfInvoice(DateHelper.toDate(LocalDateTime.of(
            LocalDate.of(2025, 3, 25),
            LocalTime.of(15, 0))))
        .build();
    var gen = InvoiceGeneration.generateStructuredReference(invoice);
    System.err.println(gen);
    assertTrue(isValidOGM(gen.replace("+", "").replace("/", "")));

  }

  static boolean isValidOGM(String ogm) {
    if (ogm == null || ogm.length() != 12)
      return false;

    String firstTen = ogm.substring(0, 10);
    String controlDigits = ogm.substring(10);

    long number = Long.parseLong(firstTen);
    int expectedCheck = (int) (number % 97);
    if (expectedCheck == 0)
      expectedCheck = 97;

    int providedCheck = Integer.parseInt(controlDigits);

    return expectedCheck == providedCheck;
  }

}
