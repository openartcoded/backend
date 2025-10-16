package tech.artcoded.websitev2.pages.invoice;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface InvoiceTemplateRepository extends MongoRepository<InvoiceFreemarkerTemplate, String> {

  List<InvoiceFreemarkerTemplate> findByLogicalDeleteIsFalse();

  Optional<InvoiceFreemarkerTemplate> findTop1ByOrderByDateCreationDesc();

}
