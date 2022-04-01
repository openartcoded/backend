package tech.artcoded.websitev2.pages.immo;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Date;

public interface ImmoFilterRepository extends MongoRepository<ImmoFilter, String> {
  void deleteByDateCreationBefore(Date date);
}
