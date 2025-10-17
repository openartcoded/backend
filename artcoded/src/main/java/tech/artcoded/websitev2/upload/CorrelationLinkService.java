package tech.artcoded.websitev2.upload;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CachePut;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CorrelationLinkService {
  public static final String CACHE_LINKS_KEY = "correlationLinks";
  private final List<ILinkable> linkables;
  private final FileUploadService uploadService;

  public CorrelationLinkService(List<ILinkable> linkables, FileUploadService uploadService) {
    this.linkables = linkables;
    this.uploadService = uploadService;
  }

  @CachePut(cacheNames = CACHE_LINKS_KEY, key = "'getLinks'")
  public Map<String, String> getLinks() {
    log.info("linkables size: {}", linkables.size());
    var correlationIds = uploadService.findAllCorrelationIds();
    log.debug("correlation ids {}", correlationIds);
    return linkables.stream().flatMap(linkable -> linkable.getCorrelationLabels(correlationIds).entrySet().stream())
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}
