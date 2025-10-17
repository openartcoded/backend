package tech.artcoded.websitev2.pages.mail;

import java.util.Optional;

import org.springframework.cache.annotation.CachePut;
import org.springframework.stereotype.Service;

import tech.artcoded.websitev2.upload.ILinkable;

@Service
public class MailJobService implements ILinkable {
  private final MailJobRepository repository;

  public MailJobService(MailJobRepository repository) {
    this.repository = repository;
  }

  @Override
  @CachePut(cacheNames = "mail_job_correlation_links", key = "#correlationId")
  public Optional<String> getCorrelationLabel(String correlationId) {
    return this.repository.findById(correlationId)
        .map(m -> "Mail %s".formatted(m.getSubject()));
  }

}
