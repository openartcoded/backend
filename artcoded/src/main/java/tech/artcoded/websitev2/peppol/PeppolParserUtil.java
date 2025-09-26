package tech.artcoded.websitev2.peppol;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Optional;
import java.util.stream.Collectors;

import com.helger.base.io.nonblocking.NonBlockingByteArrayInputStream;
import com.helger.ubl21.UBL21Marshaller;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import oasis.names.specification.ubl.schema.xsd.invoice_21.InvoiceType;
import tech.artcoded.websitev2.utils.helper.DateHelper;
import oasis.names.specification.ubl.schema.xsd.commonaggregatecomponents_21.MonetaryTotalType;
import oasis.names.specification.ubl.schema.xsd.creditnote_21.CreditNoteType;

@Slf4j
public class PeppolParserUtil {

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder(toBuilder = true)
  public static class InvoiceMetadata {
    private String id;
    private Date issueDate;
    private String supplierName;
    private String customerName;
    private String currency;
    private String description;
    private BigDecimal taxExclusiveAmount;
    private BigDecimal taxAmount;
    private BigDecimal payableAmount;
  }

  public static Optional<InvoiceMetadata> tryParse(byte[] xmlBytes) {
    try {
      return Optional.of(parseInvoice(xmlBytes));
    } catch (Exception e) {
      log.debug("could not parse as an invoice, trying with creditNote. error: \n{}", e);
      try {
        return Optional.of(parseCreditNote(xmlBytes));
      } catch (Exception e2) {
        log.debug("could not parse as a creditNote, giving up... error: \n{}", e2);
        return Optional.empty();
      }
    }
  }

  public static InvoiceMetadata parseCreditNote(byte[] xmlBytes) {
    try (NonBlockingByteArrayInputStream bis = new NonBlockingByteArrayInputStream(xmlBytes)) {
      var marshaller = UBL21Marshaller.creditNote();
      CreditNoteType creditNote = marshaller.read(bis);

      InvoiceMetadata meta = new InvoiceMetadata();

      fillCreditNoteMeta(creditNote, meta);

      return meta;
    } catch (Exception e) {
      throw new RuntimeException("Failed to parse PEPPOL/UBL XML", e);
    }
  }

  public static InvoiceMetadata parseInvoice(byte[] xmlBytes) {
    try (NonBlockingByteArrayInputStream bis = new NonBlockingByteArrayInputStream(xmlBytes)) {
      var marshaller = UBL21Marshaller.invoice();
      InvoiceType invoice = marshaller.read(bis);

      InvoiceMetadata meta = new InvoiceMetadata();

      fillInvoiceMeta(invoice, meta);

      return meta;
    } catch (Exception e) {
      throw new RuntimeException("Failed to parse PEPPOL/UBL XML", e);
    }
  }

  private static void fillInvoiceMeta(InvoiceType invoice, InvoiceMetadata meta) {
    meta.id = invoice.getIDValue();
    meta.issueDate = invoice.getIssueDateValue() != null ? DateHelper.toDate(invoice.getIssueDateValue().toLocalDate())
        : null;
    meta.currency = invoice.getDocumentCurrencyCodeValue();

    if (invoice.hasNoteEntries()) {
      meta.description = invoice.getNote().stream().map(n -> n.getValue()).collect(Collectors.joining("\n"));
    }
    if (invoice.getAccountingSupplierParty() != null &&
        invoice.getAccountingSupplierParty().getParty() != null &&
        !invoice.getAccountingSupplierParty().getParty().getPartyName().isEmpty()) {
      meta.supplierName = invoice.getAccountingSupplierParty().getParty().getPartyNameAtIndex(0).getNameValue();
    }

    if (invoice.getAccountingCustomerParty() != null &&
        invoice.getAccountingCustomerParty().getParty() != null &&
        !invoice.getAccountingCustomerParty().getParty().getPartyName().isEmpty()) {
      meta.customerName = invoice.getAccountingCustomerParty().getParty().getPartyNameAtIndex(0).getNameValue();
    }

    MonetaryTotalType legalMonetaryTotal = invoice.getLegalMonetaryTotal();
    if (legalMonetaryTotal != null) {
      meta.taxExclusiveAmount = legalMonetaryTotal.getTaxExclusiveAmountValue();
      meta.taxAmount = Optional.ofNullable(invoice.getTaxTotalAtIndex(0)).map(c -> c.getTaxAmountValue())
          .orElse(null);

      meta.payableAmount = legalMonetaryTotal.getPayableAmountValue();
    }
  }

  private static void fillCreditNoteMeta(CreditNoteType creditNote, InvoiceMetadata meta) {
    meta.id = creditNote.getIDValue();
    meta.issueDate = creditNote.getIssueDateValue() != null
        ? DateHelper.toDate(creditNote.getIssueDateValue().toLocalDate())
        : null;
    meta.currency = creditNote.getDocumentCurrencyCodeValue();

    if (creditNote.hasNoteEntries()) {
      meta.description = creditNote.getNote().stream().map(n -> n.getValue()).collect(Collectors.joining("\n"));
    }
    Optional.ofNullable(creditNote.getAccountingSupplierParty())
        .map(c -> c.getParty())
        .map(c -> c.getPartyName()).ifPresent(c -> {
          meta.supplierName = c.stream().map(p -> p.getNameValue()).collect(Collectors.joining(","));
        });
    Optional.ofNullable(creditNote.getAccountingCustomerParty())
        .map(c -> c.getParty())
        .map(c -> c.getPartyName()).ifPresent(c -> {
          meta.customerName = c.stream().map(p -> p.getNameValue()).collect(Collectors.joining(","));
        });

    MonetaryTotalType legalMonetaryTotal = creditNote.getLegalMonetaryTotal();
    if (legalMonetaryTotal != null) {
      meta.taxExclusiveAmount = legalMonetaryTotal.getTaxExclusiveAmountValue();
      meta.taxAmount = Optional.ofNullable(creditNote.getTaxTotalAtIndex(0)).map(c -> c.getTaxAmountValue())
          .orElse(null);

      meta.payableAmount = legalMonetaryTotal.getPayableAmountValue();
    }
  }
}
