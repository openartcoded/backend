package tech.artcoded.websitev2.upload;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CachePut;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CorrelationLinkService {

  private final List<ILinkable> linkables;
  private final FileUploadService uploadService;

  public CorrelationLinkService(List<ILinkable> linkables, FileUploadService uploadService) {
    this.linkables = linkables;
    this.uploadService = uploadService;
  }

  @CachePut(cacheNames = "correlationLinks", key = "'getLinks'")
  public Map<String, Optional<String>> getLinks() {
    return uploadService.findAllCorrelationIds()
        .stream()
        .map(correlationId -> Map.entry(correlationId, linkables.stream()
            .flatMap(linkable -> linkable.getCorrelationLabel(correlationId).stream()).findFirst()))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

  }

}
