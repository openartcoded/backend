package tech.artcoded.websitev2.pages.contact;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface FormContactRepository extends MongoRepository<FormContact, String> {
  List<FormContact> findByOrderByCreationDateDesc();

}
