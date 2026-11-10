package tech.artcoded.websitev2.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import tech.artcoded.websitev2.mongodb.MongoConfig;

/**
 * The template queries against a real MongoDB: per type, default first, deleted ones ignored. Skipped when no docker
 * daemon is available.
 */
@DataMongoTest
@Testcontainers(disabledWithoutDocker = true)
class OaTemplateRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:8.0"));

    @Configuration
    @EnableMongoRepositories(basePackageClasses = OaTemplateRepository.class)
    @Import(MongoConfig.class)
    static class Config {
    }

    @Autowired
    OaTemplateRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    private OaTemplate template(String id, OaTemplateType type, long created) {
        return OaTemplate.builder().id(id).name(id).type(type).dateCreation(new Date(created)).build();
    }

    @Test
    void theDefaultTemplateIsPerTypeAndNeverADeletedOne() {
        repository.save(
                template("invoice-default", OaTemplateType.INVOICE, 1000).toBuilder().defaultTemplate(true).build());
        repository.save(template("cv-deleted-default", OaTemplateType.CV, 1000).toBuilder().defaultTemplate(true)
                .logicalDelete(true).build());

        assertThat(repository.findFirstByTypeAndDefaultTemplateIsTrueAndLogicalDeleteIsFalse(OaTemplateType.INVOICE))
                .hasValueSatisfying(t -> assertThat(t.getId()).isEqualTo("invoice-default"));
        assertThat(repository.findFirstByTypeAndDefaultTemplateIsTrueAndLogicalDeleteIsFalse(OaTemplateType.CV))
                .isEmpty();
        assertThat(repository.findByTypeAndDefaultTemplateIsTrue(OaTemplateType.CV)).extracting(OaTemplate::getId)
                .containsExactly("cv-deleted-default");
    }

    @Test
    void listsArePerTypeLatestFirstWithoutDeletedOnes() {
        repository.save(template("old", OaTemplateType.INVOICE, 1000));
        repository.save(template("latest", OaTemplateType.INVOICE, 5000));
        repository.save(template("deleted", OaTemplateType.INVOICE, 9000).toBuilder().logicalDelete(true).build());
        repository.save(template("cv", OaTemplateType.CV, 10000));

        assertThat(repository.findByTypeAndLogicalDeleteIsFalseOrderByDateCreationDesc(OaTemplateType.INVOICE))
                .extracting(OaTemplate::getId).containsExactly("latest", "old");
        assertThat(repository.findByLogicalDeleteIsFalseOrderByDateCreationDesc()).extracting(OaTemplate::getId)
                .containsExactly("cv", "latest", "old");
        assertThat(repository.existsByType(OaTemplateType.TIMESHEET)).isFalse();
        assertThat(repository.existsByType(OaTemplateType.CV)).isTrue();
    }

    @Test
    void versionIsStoredAndV2ByDefault() {
        repository.save(template("v2", OaTemplateType.INVOICE, 1000));
        repository.save(template("v1", OaTemplateType.INVOICE, 1000).toBuilder().version(OaTemplateVersion.V1).build());

        assertThat(repository.findById("v2"))
                .hasValueSatisfying(t -> assertThat(t.getVersion()).isEqualTo(OaTemplateVersion.V2));
        assertThat(repository.findById("v1"))
                .hasValueSatisfying(t -> assertThat(t.getVersion()).isEqualTo(OaTemplateVersion.V1));
    }
}
