package tech.artcoded.websitev2.pages.blog;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class PostCountService {
  private final PostRepository repository;

  public PostCountService(PostRepository repository) {
    this.repository = repository;
  }

  @Cacheable(cacheNames = "ipCache", key = "#ipAddress + '_' + #postId")
  public String incrementCountForIpAddress(String postId, String ipAddress) {
    Thread.startVirtualThread(() -> {
      repository
          .findById(postId)
          .filter(post -> !post.isDraft())
          .ifPresent(post -> repository.save(post.toBuilder().countViews(post.getCountViews() + 1).build()));
    });
    return ipAddress;
  }
}
