package tech.artcoded.websitev2.pages.invoice;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface InvoiceTemplateRepository extends MongoRepository<InvoiceFreemarkerTemplate, String> {

  List<InvoiceFreemarkerTemplate> findByLogicalDeleteIsFalse();

}
