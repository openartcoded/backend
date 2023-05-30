package tech.artcoded.websitev2.pages.invoice.seq;

import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertEvent;
import org.springframework.data.mongodb.core.mapping.event.BeforeDeleteEvent;
import org.springframework.stereotype.Component;

import tech.artcoded.websitev2.pages.invoice.InvoiceGeneration;

@Component
public class InvoiceListener extends AbstractMongoEventListener<InvoiceGeneration> {

  private final InvoicePrimarySequenceService primarySequenceService;

  public InvoiceListener(final InvoicePrimarySequenceService primarySequenceService) {
    this.primarySequenceService = primarySequenceService;
  }

  @Override
  public void onBeforeConvert(final BeforeConvertEvent<InvoiceGeneration> event) {
    if (event.getSource().getId() == null) {
      event.getSource().setSeqInvoiceNumber(primarySequenceService.getNextValueAndIncrementBy(1));
    }
  }

  @Override
  public void onBeforeDelete(BeforeDeleteEvent<InvoiceGeneration> event) {
    this.primarySequenceService.getNextValueAndIncrementBy(-1);

  }

}
