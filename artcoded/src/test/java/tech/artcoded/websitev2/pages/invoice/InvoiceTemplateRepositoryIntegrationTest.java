package tech.artcoded.websitev2.pages.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import tech.artcoded.websitev2.mongodb.MongoConfig;

/**
 * The default template queries against a real MongoDB, including templates stored before the default flag existed.
 * Skipped when no docker daemon is available.
 */
@DataMongoTest
@Testcontainers(disabledWithoutDocker = true)
class InvoiceTemplateRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:8.0"));

    @Configuration
    @EnableMongoRepositories(basePackageClasses = InvoiceTemplateRepository.class)
    @Import(MongoConfig.class)
    static class Config {
    }

    @Autowired
    MongoTemplate mongoTemplate;
    @Autowired
    InvoiceTemplateRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void aTemplateStoredBeforeTheFlagExistedIsNotTheDefault() {
        mongoTemplate.getCollection(mongoTemplate.getCollectionName(InvoiceFreemarkerTemplate.class))
                .insertOne(new Document("_id", "legacy").append("name", "legacy").append("logicalDelete", false));

        assertThat(repository.findById("legacy")).hasValueSatisfying(t -> assertThat(t.isDefaultTemplate()).isFalse());
        assertThat(repository.findFirstByDefaultTemplateIsTrueAndLogicalDeleteIsFalse()).isEmpty();
        assertThat(repository.findByDefaultTemplateIsTrue()).isEmpty();
    }

    @Test
    void findsTheDefaultTemplateButNotADeletedOne() {
        repository.save(InvoiceFreemarkerTemplate.builder().id("plain").name("plain").build());
        repository.save(InvoiceFreemarkerTemplate.builder().id("deleted").name("deleted").defaultTemplate(true)
                .logicalDelete(true).build());
        assertThat(repository.findFirstByDefaultTemplateIsTrueAndLogicalDeleteIsFalse()).isEmpty();

        repository
                .save(InvoiceFreemarkerTemplate.builder().id("default").name("default").defaultTemplate(true).build());
        assertThat(repository.findFirstByDefaultTemplateIsTrueAndLogicalDeleteIsFalse())
                .hasValueSatisfying(t -> assertThat(t.getId()).isEqualTo("default"));
        assertThat(repository.findByDefaultTemplateIsTrue()).extracting(InvoiceFreemarkerTemplate::getId)
                .containsExactlyInAnyOrder("deleted", "default");
    }
}
