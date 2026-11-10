package tech.artcoded.websitev2.pages.cv.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import tech.artcoded.websitev2.pages.cv.entity.CurriculumFreemarkerTemplate;

/**
 * @deprecated legacy collection, migrated to {@link tech.artcoded.websitev2.template.OaTemplate} (oaTemplates). Only
 *             kept for the legacy change units that still write to it on a fresh database.
 */
@Deprecated
public interface CurriculumTemplateRepository extends MongoRepository<CurriculumFreemarkerTemplate, String> {
}
