package tech.artcoded.websitev2.pages.invoice;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;

import tech.artcoded.websitev2.peppol.PeppolStatus;
import tech.artcoded.websitev2.utils.helper.DateHelper;
import tech.artcoded.websitev2.utils.helper.IdGenerators;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Document
public class InvoiceGeneration implements Serializable {
  private static final long serialVersionUID = 1L;
  private static final LocalDate FORMAT_INVOICE_NUMBER_FROM_APRIL_2024 = LocalDate.of(2024, Month.APRIL, 5);

  @Id
  @Builder.Default
  private String id = IdGenerators.get();
  @Builder.Default
  @Deprecated
  private String invoiceNumber = InvoiceGeneration.generateInvoiceNumber();

  private String structuredReference;
  @Builder.Default
  private Long seqInvoiceNumber = null;

  @Builder.Default
  private Date dateOfInvoice = new Date();

  @Builder.Default
  private Date dateCreation = new Date();

  private Date archivedDate;

  @Builder.Default
  private Date updatedDate = new Date();

  private BillTo billTo;

  @Builder.Default
  private List<InvoiceRow> invoiceTable = new ArrayList<>();
  @Builder.Default
  private BigDecimal taxRate = new BigDecimal("21");
  private int maxDaysToPay;

  private boolean locked;
  private boolean archived;
  private boolean logicalDelete;
  private boolean uploadedManually;
  private String invoiceUploadId;
  private String invoiceUBLId;
  private String freemarkerTemplateId;

  private PeppolStatus peppolStatus;
  private String specialNote;

  // wheiter it was imported from an old system
  private boolean imported;
  private Date importedDate;

  private String timesheetId;

  @Deprecated
  @Transient
  public static String generateInvoiceNumber() {
    return DateTimeFormatter.ofPattern("MMyyyy") // this is now just a reference
        .format(LocalDate.now())
        .concat("-")
        .concat(RandomStringUtils.randomAlphabetic(2).toUpperCase());
  }

  @Transient
  public static String generateStructuredReference(InvoiceGeneration i) {

    if (!Optional.ofNullable(i.billTo.getVatNumber()).filter(vat -> !vat.isBlank()).isPresent()) {
      return "";
    }
    var issuedDate = DateHelper.toLocalDate(i.getDateOfInvoice());
    var baseNumber = i.billTo.getCompanyNumber().substring(4, 8)
        + StringUtils.leftPad((issuedDate.getYear() + "").substring(2, 4), 2, '0')
        + StringUtils.leftPad(issuedDate.getMonthValue() + "", 2, '0')
        + StringUtils.leftPad(issuedDate.getDayOfMonth() + "", 2, '0');

    long num = Long.parseLong(baseNumber);
    int checksum = (int) (num % 97);
    if (checksum == 0)
      checksum = 97;

    String fullReference = baseNumber + StringUtils.leftPad((checksum + ""), 2, '0');

    return "+++" + fullReference.substring(0, 3) + "/" +
        fullReference.substring(3, 7) + "/" +
        fullReference.substring(7) + "+++";
  }

  @Transient
  public String getNewInvoiceNumber() {
    if (this.seqInvoiceNumber == null || this.seqInvoiceNumber <= 0) {
      return null; // todo we may want to rollback to the old invoice number in this case.
    }

    // 20240406:
    // Pourriez-vous à partir d’avril 2024, changer la numérotation de vos factures
    // de ventes,
    // il ne faut pas reprendre le mois de facturation.
    // La prochaine sur avril selon moi devrais donc être 2024.08 ou 2024 008,
    // si vous pensez faire plus de 99 factures sur l’année.

    var dateOfInvoice = DateHelper.toLocalDate(Optional.ofNullable(this.dateOfInvoice).orElse(this.dateCreation));

    if (dateOfInvoice.isAfter(FORMAT_INVOICE_NUMBER_FROM_APRIL_2024)) {
      return this.getInvoiceTable().stream().findFirst().map(p -> p.getPeriod())
          .filter(Objects::nonNull)
          .flatMap(p -> Arrays.stream(p.split("/")).skip(1).findFirst())
          .filter(StringUtils::isNotEmpty)
          .orElseGet(() -> DateTimeFormatter.ofPattern("yyyy").format(LocalDateTime.now()).toString())
          + StringUtils.leftPad(this.seqInvoiceNumber.toString(), 3, '0');

    } else {
      return this.getInvoiceTable().stream().findFirst().map(p -> p.getPeriod())
          .filter(Objects::nonNull)
          .map(p -> p.replace("/", ""))
          .filter(StringUtils::isNotEmpty)
          .map(p -> p.concat("-"))
          .orElse("") + this.seqInvoiceNumber;

    }

  }

  @Transient
  public String getReference() {
    return this.invoiceNumber;
  }

  @Transient
  public BigDecimal getSubTotal() {
    return invoiceTable.stream()
        .map(InvoiceRow::getTotal)
        .reduce(new BigDecimal(0), BigDecimal::add)
        .setScale(2, RoundingMode.DOWN);
  }

  @Transient
  public String getClientName() {
    return getBillTo().getClientName();
  }

  @Transient
  public Date getDueDate() {
    ZoneId zone = ZoneId.systemDefault();
    return Date.from(
        this.dateOfInvoice
            .toInstant()
            .atZone(zone)
            .toLocalDate()
            .plusDays(maxDaysToPay)
            .atStartOfDay(zone)
            .toInstant());
  }

  @Transient
  public BigDecimal getTaxable() {
    return this.getSubTotal();
  }

  @Transient
  public BigDecimal getTaxes() {
    return this.getSubTotal()
        .multiply(taxRate.divide(new BigDecimal("100"), 2, RoundingMode.UNNECESSARY))
        .setScale(2, RoundingMode.HALF_UP);
  }

  @Transient
  public BigDecimal getTotal() {
    return this.getSubTotal().add(this.getTaxes()).setScale(2, RoundingMode.DOWN);
  }
}
