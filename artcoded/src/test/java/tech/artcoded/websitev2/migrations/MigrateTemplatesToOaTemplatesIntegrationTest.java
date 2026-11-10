package tech.artcoded.websitev2.migrations;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;

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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import tech.artcoded.websitev2.mongodb.MongoConfig;
import tech.artcoded.websitev2.template.OaTemplate;
import tech.artcoded.websitev2.template.OaTemplateRepository;
import tech.artcoded.websitev2.template.OaTemplateType;
import tech.artcoded.websitev2.template.OaTemplateVersion;

/** The legacy invoice and cv template collections become v1 oaTemplates, with the same ids. */
@DataMongoTest
@Testcontainers(disabledWithoutDocker = true)
class MigrateTemplatesToOaTemplatesIntegrationTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:8.0"));

    @Configuration
    @EnableMongoRepositories(basePackageClasses = OaTemplateRepository.class)
    @Import(MongoConfig.class)
    static class Config {
    }

    @Autowired
    MongoTemplate mongoTemplate;
    @Autowired
    OaTemplateRepository repository;

    final _0002__MigrateTemplatesToOaTemplates change = new _0002__MigrateTemplatesToOaTemplates();

    @BeforeEach
    void seedLegacyTemplates() {
        mongoTemplate.getDb().drop();
        // as stored in production, including a template from before the default / logical delete flags
        mongoTemplate.getCollection("invoiceFreemarkerTemplate").insertMany(java.util.List.of(
                new Document("_id", "inv-2021").append("name", "Template 2021").append("dateCreation", new Date(1000))
                        .append("templateUploadId", "upl-1").append("_class", "x"),
                new Document("_id", "inv-2022").append("name", "Template End 2022")
                        .append("dateCreation", new Date(2000)).append("templateUploadId", "upl-2")
                        .append("defaultTemplate", true).append("logicalDelete", false),
                new Document("_id", "inv-deleted").append("name", "old").append("dateCreation", new Date(500))
                        .append("templateUploadId", "upl-3").append("logicalDelete", true)));
        mongoTemplate.getCollection("curriculumFreemarkerTemplate").insertMany(java.util.List.of(
                new Document("_id", "cv-2021").append("name", "Template 2021").append("dateCreation", new Date(1000))
                        .append("templateUploadId", "upl-cv-1"),
                new Document("_id", "cv-2020").append("name", "Template 2020").append("dateCreation", new Date(900))
                        .append("templateUploadId", "upl-cv-2")));
        mongoTemplate.getCollection("curriculum")
                .insertOne(new Document("_id", "cv").append("freemarkerTemplateId", "cv-2021"));
    }

    @Test
    void legacyTemplatesBecomeV1TemplatesWithTheirIds() {
        change.apply(mongoTemplate);

        assertThat(repository.findAll()).hasSize(5).allSatisfy(t -> {
            assertThat(t.getVersion()).isEqualTo(OaTemplateVersion.V1);
            assertThat(t.getFileServiceTemplateId()).isNull();
        });
        OaTemplate inv2022 = repository.findById("inv-2022").orElseThrow();
        assertThat(inv2022.getType()).isEqualTo(OaTemplateType.INVOICE);
        assertThat(inv2022.getName()).isEqualTo("Template End 2022");
        assertThat(inv2022.getTemplateUploadId()).isEqualTo("upl-2");
        assertThat(inv2022.isDefaultTemplate()).isTrue();
        assertThat(inv2022.getDateCreation()).isEqualTo(new Date(2000));

        var inv2021 = repository.findById("inv-2021").orElseThrow();
        assertThat(inv2021.isDefaultTemplate()).isFalse();
        assertThat(inv2021.isLogicalDelete()).isFalse();
        assertThat(repository.findById("inv-deleted").orElseThrow().isLogicalDelete()).isTrue();

        // the cv template in use becomes the default cv template
        assertThat(repository.findById("cv-2021").orElseThrow()).satisfies(t -> {
            assertThat(t.getType()).isEqualTo(OaTemplateType.CV);
            assertThat(t.isDefaultTemplate()).isTrue();
        });
        assertThat(repository.findById("cv-2020").orElseThrow().isDefaultTemplate()).isFalse();

        // legacy collections untouched
        assertThat(mongoTemplate.getCollection("invoiceFreemarkerTemplate").countDocuments()).isEqualTo(3);
        assertThat(mongoTemplate.getCollection("curriculumFreemarkerTemplate").countDocuments()).isEqualTo(2);
    }

    @Test
    void runningTwiceDoesNotDuplicateNorOverwrite() {
        change.apply(mongoTemplate);
        repository.save(repository.findById("inv-2021").orElseThrow().toBuilder().name("renamed").build());

        change.apply(mongoTemplate);

        assertThat(repository.count()).isEqualTo(5);
        assertThat(repository.findById("inv-2021").orElseThrow().getName()).isEqualTo("renamed");
    }

    @Test
    void rollbackOnlyRemovesTheMigratedTemplates() {
        change.apply(mongoTemplate);
        repository.save(OaTemplate.builder().id("new-v2").name("new").type(OaTemplateType.INVOICE).build());

        change.rollback(mongoTemplate);

        assertThat(repository.findAll()).extracting(OaTemplate::getId).containsExactly("new-v2");
    }

    @Test
    void withoutFlaggedDefaultTheLatestTemplateNotDeletedBecomesTheDefault() {
        mongoTemplate.getCollection("invoiceFreemarkerTemplate").updateMany(new Document(),
                new Document("$set", new Document("defaultTemplate", false)));
        mongoTemplate.getCollection("curriculum").deleteMany(new Document());

        change.apply(mongoTemplate);

        assertThat(repository.findByTypeAndDefaultTemplateIsTrue(OaTemplateType.INVOICE))
                .extracting(OaTemplate::getId).containsExactly("inv-2022");
        assertThat(repository.findByTypeAndDefaultTemplateIsTrue(OaTemplateType.CV)).extracting(OaTemplate::getId)
                .containsExactly("cv-2021");
    }

    @Test
    void aDefaultAlreadyInOaTemplatesIsKept() {
        // fresh database: a legacy change unit already added an invoice template, default, to oaTemplates
        repository.save(OaTemplate.builder().id("new").name("new").type(OaTemplateType.INVOICE)
                .version(OaTemplateVersion.V1).defaultTemplate(true).build());

        change.apply(mongoTemplate);

        assertThat(repository.findByTypeAndDefaultTemplateIsTrue(OaTemplateType.INVOICE))
                .extracting(OaTemplate::getId).containsExactly("new");
    }
}
