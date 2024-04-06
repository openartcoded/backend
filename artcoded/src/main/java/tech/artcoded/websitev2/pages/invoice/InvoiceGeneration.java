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
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Document
public class InvoiceGeneration implements Serializable {
  private static final long serialVersionUID = 1L;
  @Id
  @Builder.Default
  private String id = IdGenerators.get();
  @Builder.Default
  @Deprecated
  private String invoiceNumber = InvoiceGeneration.generateInvoiceNumber();

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
  private String freemarkerTemplateId;

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
  public String getNewInvoiceNumber() {
    if (this.seqInvoiceNumber == null || this.seqInvoiceNumber <= 0) {
      return null; // todo we may want to rollback to the old invoice number in this case.
    }
    // Disabled after accountant's feedback:
    // Pourriez-vous à partir d’avril 2024, changer la numérotation de vos factures
    // de ventes,
    // il ne faut pas reprendre le mois de facturation.
    // La prochaine sur avril selon moi devrais donc être 2024.08 ou 2024 008,
    // si vous pensez faire plus de 99 factures sur l’année.

    var formatFromApril2024 = LocalDate.of(2024, Month.APRIL, 6);
    var dateOfInvoice = DateHelper.toLocalDate(Optional.ofNullable(this.dateOfInvoice).orElse(this.dateCreation));

    String seq;

    if (dateOfInvoice.isAfter(formatFromApril2024)) {
      seq = this.getInvoiceTable().stream().findFirst().map(p -> p.getPeriod())
          .filter(Objects::nonNull)
          .flatMap(p -> Arrays.stream(p.split("/")).skip(1).findFirst())
          .filter(StringUtils::isNotEmpty)
          .orElseGet(() -> DateTimeFormatter.ofPattern("yyyy").format(LocalDateTime.now()).toString());

    } else {
      seq = this.getInvoiceTable().stream().findFirst().map(p -> p.getPeriod())
          .filter(Objects::nonNull)
          .map(p -> p.replace("/", ""))
          .filter(StringUtils::isNotEmpty)
          .map(p -> p.concat("-"))
          .orElse("");

    }

    return seq + StringUtils.leftPad(this.seqInvoiceNumber.toString(), 3, '0');
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
