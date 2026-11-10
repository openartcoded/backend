package tech.artcoded.websitev2.pages.invoice;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * @deprecated legacy collection, migrated to {@link tech.artcoded.websitev2.template.OaTemplate} (oaTemplates). Only
 *             kept for the legacy change units that still write to it on a fresh database.
 */
@Deprecated
public interface InvoiceTemplateRepository extends MongoRepository<InvoiceFreemarkerTemplate, String> {

    List<InvoiceFreemarkerTemplate> findByLogicalDeleteIsFalse();

    Optional<InvoiceFreemarkerTemplate> findTop1ByOrderByDateCreationDesc();

    Optional<InvoiceFreemarkerTemplate> findFirstByDefaultTemplateIsTrueAndLogicalDeleteIsFalse();

    List<InvoiceFreemarkerTemplate> findByDefaultTemplateIsTrue();

}
