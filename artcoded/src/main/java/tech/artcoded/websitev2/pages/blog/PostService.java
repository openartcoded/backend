package tech.artcoded.websitev2.pages.blog;

import java.util.Optional;

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
  public Optional<String> getCorrelationLabel(String correlationId) {
    return this.postRepository.findById(correlationId)
        .map(post -> "Blog post '%s' ".formatted(post.getTitle()));
  }
}
