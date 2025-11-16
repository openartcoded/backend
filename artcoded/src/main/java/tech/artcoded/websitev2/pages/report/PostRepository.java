package tech.artcoded.websitev2.pages.report;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import tech.artcoded.websitev2.pages.report.Post.PostStatus;

public interface PostRepository extends MongoRepository<Post, String> {

    Page<Post> findByStatusIsOrderByUpdatedDateDesc(PostStatus status, Pageable pageable);

    Page<Post> findByOrderByUpdatedDateDesc(Pageable pageable);

    Page<Post> findByStatusIsAndCoverIdIsNotNullOrderByUpdatedDateDesc(PostStatus status, Pageable pageable);

    Page<Post> findByBookmarkedIsOrderByBookmarkedDateDesc(boolean bookmarked, Pageable pageable);

    boolean existsByTags(String tag);

}
