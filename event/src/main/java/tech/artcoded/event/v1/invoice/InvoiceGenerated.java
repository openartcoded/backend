package tech.artcoded.event.v1.invoice;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class InvoiceGenerated implements IInvoiceEvent {
  private String invoiceId;
  private String uploadId;
  private boolean manualUpload;
  private BigDecimal subTotal;
  private BigDecimal taxes;
  private String invoiceNumber;
  private Date dateOfInvoice;
  private Date dueDate;

}
