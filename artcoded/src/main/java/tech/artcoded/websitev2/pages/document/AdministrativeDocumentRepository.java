package tech.artcoded.websitev2.pages.document;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AdministrativeDocumentRepository extends MongoRepository<AdministrativeDocument, String> {
  List<AdministrativeDocument> findByOrderByDateCreationDesc();
}
