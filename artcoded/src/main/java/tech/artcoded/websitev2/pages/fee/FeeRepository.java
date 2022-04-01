package tech.artcoded.websitev2.pages.fee;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface FeeRepository extends MongoRepository<Fee, String> {
  List<Fee> findByOrderByDateCreationDesc();
}
