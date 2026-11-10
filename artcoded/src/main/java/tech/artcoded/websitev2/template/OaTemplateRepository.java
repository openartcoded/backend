package tech.artcoded.websitev2.template;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface OaTemplateRepository extends MongoRepository<OaTemplate, String> {

    List<OaTemplate> findByLogicalDeleteIsFalseOrderByDateCreationDesc();

    List<OaTemplate> findByTypeAndLogicalDeleteIsFalseOrderByDateCreationDesc(OaTemplateType type);

    Optional<OaTemplate> findFirstByTypeAndDefaultTemplateIsTrueAndLogicalDeleteIsFalse(OaTemplateType type);

    List<OaTemplate> findByTypeAndDefaultTemplateIsTrue(OaTemplateType type);

    boolean existsByType(OaTemplateType type);
}
