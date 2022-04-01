package tech.artcoded.websitev2.pages.blog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PostRepository extends MongoRepository<Post, String> {
  Page<Post> findByDraftIsOrderByUpdatedDateDesc(boolean draft, Pageable pageable);

  Page<Post> findByDraftIsAndCoverIdIsNotNullOrderByUpdatedDateDesc(boolean draft, Pageable pageable);


  boolean existsByTags(String tag);
}
