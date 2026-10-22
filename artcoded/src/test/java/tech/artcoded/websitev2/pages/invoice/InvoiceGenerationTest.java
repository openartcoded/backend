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
        InvoiceRow row = InvoiceRow.builder().amount(new BigDecimal("12")).amountType(RateType.DAYS)
                .rate(new BigDecimal("63.38")).rateType(RateType.HOURS).build();
        Assertions.assertThat(row.getTotal()).isEqualTo(new BigDecimal("6084.48"));
        InvoiceGeneration invoice = InvoiceGeneration.builder().invoiceTable(List.of(row)).build();
        Assertions.assertThat(invoice.getTaxes()).isEqualTo(new BigDecimal("1277.74"));
        Assertions.assertThat(invoice.getTotal()).isEqualTo(new BigDecimal("7362.22"));

    }

    @Test
    public void twoRowsWithDifferentRates() {
        InvoiceRow first = InvoiceRow.builder().amount(new BigDecimal("10")).amountType(RateType.HOURS)
                .rate(new BigDecimal("50")).rateType(RateType.HOURS).build();
        InvoiceRow second = first.toBuilder().rate(new BigDecimal("75.50")).build();
        InvoiceGeneration invoice = InvoiceGeneration.builder().invoiceTable(List.of(first, second)).build();
        Assertions.assertThat(first.getTotal()).isEqualTo(new BigDecimal("500.00"));
        Assertions.assertThat(second.getTotal()).isEqualTo(new BigDecimal("755.00"));
        Assertions.assertThat(invoice.getSubTotal()).isEqualTo(new BigDecimal("1255.00"));
        Assertions.assertThat(invoice.getTaxes()).isEqualTo(new BigDecimal("263.55"));
        Assertions.assertThat(invoice.getTotal()).isEqualTo(new BigDecimal("1518.55"));
    }

    @Test
    public void ensureMinimumRowsCopiesLastRow() {
        InvoiceRow row = InvoiceRow.builder().projectName("p").nature("dev").period(null).amount(BigDecimal.ZERO)
                .amountType(RateType.DAYS).rate(new BigDecimal("400")).rateType(RateType.DAYS).build();

        var rows = InvoiceService.ensureMinimumRows(List.of(row), 2);
        Assertions.assertThat(rows).hasSize(2);
        Assertions.assertThat(rows.get(1)).isEqualTo(rows.get(0)).isNotSameAs(rows.get(0));
        // editing one row must not impact the other
        rows.get(1).setRate(new BigDecimal("500"));
        Assertions.assertThat(rows.get(0).getRate()).isEqualTo(new BigDecimal("400"));

        Assertions.assertThat(InvoiceService.ensureMinimumRows(List.of(row, row, row), 2)).hasSize(3);
        Assertions.assertThat(InvoiceService.ensureMinimumRows(List.of(), 2)).hasSize(2);
        Assertions.assertThat(InvoiceService.ensureMinimumRows(List.of(row), 1)).hasSize(1);
    }

    @Test
    public void validateGenBankReference() {
        InvoiceGeneration invoice = InvoiceGeneration.builder().seqInvoiceNumber(1L)
                .billTo(BillTo.builder().vatNumber("1234567890").build()).invoiceTable(List.of())
                .dateOfInvoice(DateHelper.toDate(LocalDateTime.of(LocalDate.of(2025, 3, 25), LocalTime.of(15, 0))))
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
