package tech.artcoded.websitev2.pages.invoice;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
  public String getCorrelationLabel(String correlationId) {
    return this.repository.findById(correlationId)
        .map(toLabel()).orElse(null);
  }

  private Function<? super InvoiceFreemarkerTemplate, ? extends String> toLabel() {
    return t -> "Invoice Template %s".formatted(t.getName());
  }

  @Override
  @CachePut(cacheNames = "invoice_template_all_correlation_links", key = "'allLinks'")
  public Map<String, String> getCorrelationLabels(Collection<String> correlationIds) {
    return this.repository.findAllById(correlationIds)
        .stream()
        .map(f -> Map.entry(f.getId(), this.toLabel().apply(f)))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}
