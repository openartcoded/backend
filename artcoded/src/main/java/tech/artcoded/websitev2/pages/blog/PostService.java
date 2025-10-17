package tech.artcoded.websitev2.pages.blog;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CachePut;
import org.springframework.stereotype.Service;

import tech.artcoded.websitev2.upload.ILinkable;

@Service
public class PostService implements ILinkable {
  private final PostRepository postRepository;

  public PostService(PostRepository postRepository) {
    this.postRepository = postRepository;
  }

  @Override
  @CachePut(cacheNames = "blogpost_correlation_links", key = "#correlationId")
  public String getCorrelationLabel(String correlationId) {
    return this.postRepository.findById(correlationId)
        .map(post -> toLabel(post)).orElse(null);
  }

  private String toLabel(Post post) {
    return "Blog post '%s' ".formatted(post.getTitle());
  }

  @Override
  @CachePut(cacheNames = "blogpost_all_correlation_links", key = "'allLinks'")
  public Map<String, String> getCorrelationLabels(Collection<String> correlationIds) {
    return this.postRepository.findAllById(correlationIds)
        .stream()
        .map(doc -> Map.entry(doc.getId(), this.toLabel(doc)))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

}
