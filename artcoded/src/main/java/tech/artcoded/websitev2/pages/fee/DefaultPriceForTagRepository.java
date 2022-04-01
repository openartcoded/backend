package tech.artcoded.websitev2.pages.fee;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface DefaultPriceForTagRepository extends MongoRepository<DefaultPriceForTag, String> {
  List<DefaultPriceForTag> findByTagIsNotIn(List<Tag> notFixedPrice);

  Optional<DefaultPriceForTag> findByTag(Tag tag);
}
