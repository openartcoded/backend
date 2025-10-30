package tech.artcoded.websitev2.pages.postit;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface PostItRepository extends MongoRepository<PostIt, String> {

}
