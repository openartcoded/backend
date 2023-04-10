package tech.artcoded.websitev2.pages.invoice;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;
import tech.artcoded.websitev2.utils.helper.IdGenerators;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Document
public class InvoiceGeneration {
  @Id
  @Builder.Default
  private String id = IdGenerators.get();
  @Builder.Default
  private String invoiceNumber = InvoiceGeneration.generateInvoiceNumber();
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

  public static String generateInvoiceNumber() {
    return DateTimeFormatter.ofPattern("MMyyyy")
        .format(LocalDate.now())
        .concat("-")
        .concat(RandomStringUtils.randomAlphabetic(2).toUpperCase());
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
