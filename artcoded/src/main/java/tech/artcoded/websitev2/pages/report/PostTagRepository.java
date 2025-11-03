package tech.artcoded.websitev2.pages.report;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface PostTagRepository extends MongoRepository<PostTag, String> {
}
