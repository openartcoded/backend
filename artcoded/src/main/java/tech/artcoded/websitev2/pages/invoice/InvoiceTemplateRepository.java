package tech.artcoded.websitev2.pages.invoice;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface InvoiceTemplateRepository extends MongoRepository<InvoiceFreemarkerTemplate, String> {
}
