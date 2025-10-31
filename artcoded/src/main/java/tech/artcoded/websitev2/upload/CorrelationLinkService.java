package tech.artcoded.websitev2.upload;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CachePut;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CorrelationLinkService {
  public static final String CACHE_LINKS_KEY = "correlationLinks";
  private final List<ILinkable> linkables;
  private final IFileUploadService uploadService;

  public CorrelationLinkService(List<ILinkable> linkables, IFileUploadService uploadService) {
    this.linkables = linkables;
    this.uploadService = uploadService;
  }

  @CachePut(cacheNames = CACHE_LINKS_KEY, key = "'getLinks'")
  public Map<String, String> getLinks() {
    var correlationIds = uploadService.findAllCorrelationIds();
    log.debug("correlation ids {}", correlationIds);
    return linkables.stream().flatMap(linkable -> linkable.getCorrelationLabels(correlationIds).entrySet().stream())
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  @PostConstruct
  public void logLinkables() {
    log.info("linkables size: {}", linkables.size());
    linkables.stream().forEach(linkable -> log.info("Loaded Linkable {}", linkable.getClass().getSimpleName()));
  }
}
