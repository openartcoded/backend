package tech.artcoded.event.v1.invoice;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class InvoiceRemoved implements IInvoiceEvent {
    private String invoiceId;
    private String uploadId;
    private boolean logicalDelete;

}
