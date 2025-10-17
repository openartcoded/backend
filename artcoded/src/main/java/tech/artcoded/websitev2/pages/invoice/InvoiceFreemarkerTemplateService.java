package tech.artcoded.websitev2.pages.invoice;

import java.util.Optional;

import org.springframework.cache.annotation.CachePut;
import org.springframework.stereotype.Service;

import tech.artcoded.websitev2.upload.ILinkable;

@Service
public class InvoiceFreemarkerTemplateService implements ILinkable {

  private final InvoiceTemplateRepository repository;

  public InvoiceFreemarkerTemplateService(InvoiceTemplateRepository repository) {
    this.repository = repository;
  }

  @Override
  @CachePut(cacheNames = "invoice_template_correlation_links", key = "#correlationId")
  public Optional<String> getCorrelationLabel(String correlationId) {
    return this.repository.findById(correlationId)
        .map(t -> "Invoice Template %s".formatted(t.getName()));
  }

}
