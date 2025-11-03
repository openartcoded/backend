package tech.artcoded.websitev2.pages.mail;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    public String getCorrelationLabel(String correlationId) {
        return this.repository.findById(correlationId).map(toLabel()).orElse(null);
    }

    private Function<? super MailJob, ? extends String> toLabel() {
        return m -> "Mail %s".formatted(m.getSubject());
    }

    @Override
    @CachePut(cacheNames = "mail_job_all_correlation_links", key = "'allLinks'")
    public Map<String, String> getCorrelationLabels(Collection<String> correlationIds) {
        return this.repository.findAllById(correlationIds).stream()
                .map(f -> Map.entry(f.getId(), this.toLabel().apply(f)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public void updateOldId(String correlationId, String oldId, String newId) {
        this.repository.findById(correlationId).ifPresent(p -> {
            var changed = false;
            if (Optional.ofNullable(p.getUploadIds()).orElse(List.of()).contains(oldId)) {
                p.setUploadIds(Stream.concat(p.getUploadIds().stream().filter(t -> !oldId.equals(t)), Stream.of(newId))
                        .toList());
                changed = true;
            }

            if (changed) {
                this.repository.save(p.toBuilder().updatedDate(new Date()).build());
            }
        });

    }
}
