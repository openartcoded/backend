package tech.artcoded.event.v1.invoice;

import com.fasterxml.jackson.annotation.JsonProperty;

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
    private String ublId;
    private String bankReference;
    private String timesheetId;
    private boolean manualUpload;
    private BigDecimal subTotal;
    private BigDecimal taxes;
    private String invoiceNumber;
    private String referenceNumber;
    private String creditNoteReference;
    private Long seq;
    private Date dateOfInvoice;
    private Date dueDate;

    /**
     * Derived value: written in the JSON for consumers but read-only, so a strict consumer can deserialize the event
     * back (it used to fail with an unrecognized field "creditNote"). Null-safe for partially filled events.
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public boolean isCreditNote() {
        var sub = subTotal == null ? BigDecimal.ZERO : subTotal;
        var tax = taxes == null ? BigDecimal.ZERO : taxes;
        return sub.add(tax).signum() < 0;
    }

}
