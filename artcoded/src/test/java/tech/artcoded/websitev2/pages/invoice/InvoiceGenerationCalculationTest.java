package tech.artcoded.websitev2.pages.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import tech.artcoded.websitev2.domain.common.RateType;
import tech.artcoded.websitev2.utils.helper.DateHelper;

class InvoiceGenerationCalculationTest {

    private static InvoiceRow hours(String amount, String rate) {
        return InvoiceRow.builder().amount(new BigDecimal(amount)).amountType(RateType.HOURS).rate(new BigDecimal(rate))
                .rateType(RateType.HOURS).period("10/2026").build();
    }

    private static InvoiceGeneration invoice(String taxRate, InvoiceRow... rows) {
        return InvoiceGeneration.builder().taxRate(new BigDecimal(taxRate)).invoiceTable(List.of(rows)).build();
    }

    @ParameterizedTest(name = "VAT {0}% on 100.00 = {1}")
    @CsvSource({ "21, 21.00", "12, 12.00", "6, 6.00", "0, 0.00", "5.5, 5.50", "8.1, 8.10", "0.25, 0.25" })
    void taxesSupportAnyVatRate(String taxRate, String expectedTaxes) {
        var invoice = invoice(taxRate, hours("2", "50"));
        // 5.5 used to throw an ArithmeticException (divide(.., 2, UNNECESSARY))
        assertThatCode(invoice::getTaxes).doesNotThrowAnyException();
        assertThat(invoice.getSubTotal()).isEqualByComparingTo("100.00");
        assertThat(invoice.getTaxes()).isEqualByComparingTo(expectedTaxes);
        assertThat(invoice.getTotal())
                .isEqualByComparingTo(new BigDecimal("100.00").add(new BigDecimal(expectedTaxes)));
    }

    @Test
    void taxesAreRoundedHalfUp() {
        // 10.05 * 21% = 2.1105 -> 2.11
        assertThat(invoice("21", hours("1", "10.05")).getTaxes()).isEqualByComparingTo("2.11");
        // 0.10 * 25% = 0.025 -> 0.03
        assertThat(invoice("25", hours("1", "0.10")).getTaxes()).isEqualByComparingTo("0.03");
    }

    @Test
    void emptyInvoiceHasZeroTotals() {
        var invoice = invoice("21");
        assertThat(invoice.getSubTotal()).isEqualByComparingTo("0");
        assertThat(invoice.getTaxable()).isEqualByComparingTo("0");
        assertThat(invoice.getTaxes()).isEqualByComparingTo("0");
        assertThat(invoice.getTotal()).isEqualByComparingTo("0");
        assertThat(invoice.isCreditNote()).isFalse();
    }

    @Test
    void negativeTotalMeansCreditNote() {
        assertThat(invoice("21", hours("-3", "100")).isCreditNote()).isTrue();
        assertThat(invoice("21", hours("3", "100"), hours("-1", "100")).isCreditNote()).isFalse();
    }

    @Test
    void newInvoiceNumberIsNullWithoutSequence() {
        assertThat(InvoiceGeneration.builder().seqInvoiceNumber(null).build().getNewInvoiceNumber()).isNull();
        assertThat(InvoiceGeneration.builder().seqInvoiceNumber(0L).build().getNewInvoiceNumber()).isNull();
        assertThat(InvoiceGeneration.builder().seqInvoiceNumber(-1L).build().getNewInvoiceNumber()).isNull();
    }

    @Test
    void newInvoiceNumberSinceApril2024UsesYearOfPeriodAndThreeDigitSequence() {
        var invoice = InvoiceGeneration.builder().seqInvoiceNumber(7L)
                .dateOfInvoice(DateHelper.toDate(LocalDate.of(2026, 11, 3))).invoiceTable(List.of(hours("1", "1")))
                .build();
        assertThat(invoice.getNewInvoiceNumber()).isEqualTo("2026007");
        assertThat(invoice.toBuilder().seqInvoiceNumber(123L).build().getNewInvoiceNumber()).isEqualTo("2026123");
    }

    @Test
    void newInvoiceNumberWithoutPeriodFallsBackToCurrentYear() {
        var invoice = InvoiceGeneration.builder().seqInvoiceNumber(12L)
                .dateOfInvoice(DateHelper.toDate(LocalDate.of(2026, 11, 3))).invoiceTable(List.of()).build();
        assertThat(invoice.getNewInvoiceNumber()).isEqualTo(Year.now().getValue() + "012");
    }

    @Test
    void newInvoiceNumberBeforeApril2024KeptTheLegacyFormat() {
        var row = hours("1", "1").toBuilder().period("03/2024").build();
        var invoice = InvoiceGeneration.builder().seqInvoiceNumber(7L)
                .dateOfInvoice(DateHelper.toDate(LocalDate.of(2024, 3, 31))).invoiceTable(List.of(row)).build();
        assertThat(invoice.getNewInvoiceNumber()).isEqualTo("032024-7");
    }

    @Test
    void structuredReferenceIsAValidBelgianOgm() {
        var invoice = InvoiceGeneration.builder().billTo(BillTo.builder().vatNumber("BE 0123.456.749").build())
                .dateOfInvoice(DateHelper.toDate(LocalDate.of(2025, 3, 25))).build();
        var ogm = InvoiceGeneration.generateStructuredReference(invoice);
        assertThat(ogm).matches("\\+\\+\\+\\d{3}/\\d{4}/\\d{5}\\+\\+\\+");
        var digits = ogm.replaceAll("[^0-9]", "");
        // 4 digits of the company number + yyMMdd
        assertThat(digits).startsWith("4567250325");
        assertThat(InvoiceGenerationTest.isValidOGM(digits)).isTrue();
    }

    @Test
    void structuredReferenceUses97WhenModuloIsZero() {
        // find a date where base % 97 == 0 is cumbersome, so check the property on many dates instead
        var start = LocalDate.of(2025, 1, 1);
        for (int i = 0; i < 400; i++) {
            var invoice = InvoiceGeneration.builder().billTo(BillTo.builder().vatNumber("BE0999999999").build())
                    .dateOfInvoice(DateHelper.toDate(start.plusDays(i))).build();
            var digits = InvoiceGeneration.generateStructuredReference(invoice).replaceAll("[^0-9]", "");
            assertThat(InvoiceGenerationTest.isValidOGM(digits)).as(digits).isTrue();
            assertThat(digits.substring(10)).isNotEqualTo("00");
        }
    }

    @Test
    void noStructuredReferenceWithoutVatNumber() {
        var withoutVat = InvoiceGeneration.builder().billTo(BillTo.builder().vatNumber(null).build()).build();
        assertThat(InvoiceGeneration.generateStructuredReference(withoutVat)).isEmpty();
        var blankVat = InvoiceGeneration.builder().billTo(BillTo.builder().vatNumber("  ").build()).build();
        assertThat(InvoiceGeneration.generateStructuredReference(blankVat)).isEmpty();
    }

    @Test
    void dueDateAddsMaxDaysToPay() {
        var invoice = InvoiceGeneration.builder().dateOfInvoice(DateHelper.toDate(LocalDate.of(2026, 12, 20)))
                .maxDaysToPay(30).build();
        assertThat(DateHelper.toLocalDateTime(invoice.getDueDate()).toLocalDate()).isEqualTo(LocalDate.of(2027, 1, 19));
    }

    @Test
    void billToCompanyNumberIsCleanedAndPadded() {
        assertThat(BillTo.builder().vatNumber("BE 0123.456.749").build().getCompanyNumber()).isEqualTo("0123456749");
        assertThat(BillTo.builder().vatNumber("123456749").build().getCompanyNumber()).isEqualTo("0123456749");
        assertThat(BillTo.builder().vatNumber(null).build().getCompanyNumber()).isEqualTo("0000000000");
    }

    @Test
    void legacyInvoiceNumberHasMonthYearAndTwoLetters() {
        @SuppressWarnings("deprecation")
        var number = InvoiceGeneration.generateInvoiceNumber();
        assertThat(number).matches("\\d{6}-[A-Z]{2}");
    }
}
