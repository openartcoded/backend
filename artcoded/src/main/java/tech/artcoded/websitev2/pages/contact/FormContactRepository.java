package tech.artcoded.websitev2.pages.contact;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Date;
import java.util.List;

public interface FormContactRepository extends MongoRepository<FormContact, String> {
    List<FormContact> findByOrderByCreationDateDesc();

    void deleteByCreationDateBefore(Date dateBefore);

    long countByCreationDateBefore(Date dateBefore);
}
