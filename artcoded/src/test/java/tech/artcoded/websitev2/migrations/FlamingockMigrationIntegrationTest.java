package tech.artcoded.websitev2.migrations;

import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_CHANGE_ID;
import static io.flamingock.internal.util.constants.AuditEntryFieldConstants.KEY_STATE;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.mockito.Mockito;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration;
import org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Import;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import io.flamingock.internal.core.builder.AbstractChangeRunnerBuilder;
import io.flamingock.springboot.FlamingockAutoConfiguration;
import io.mongock.api.annotations.ChangeUnit;
import tech.artcoded.websitev2.mongodb.MongoConfig;
import tech.artcoded.websitev2.pages.personal.PersonalInfo;
import tech.artcoded.websitev2.pages.settings.menu.MenuLink;
import tech.artcoded.websitev2.pages.dossier.report.DossierQuarterlyReportAction;
import tech.artcoded.websitev2.pages.dossier.report.DossierReportAction;
import tech.artcoded.websitev2.pages.dossier.report.DossierYearlyReportAction;
import tech.artcoded.websitev2.pages.task.ReminderTask;
import tech.artcoded.websitev2.template.JinjaTemplatesInstaller;
import tech.artcoded.websitev2.template.OaTemplateType;

/**
 * Mongock to Flamingock switch, against a real MongoDB (standalone, like production: no transactions).
 * <p>
 * Each test seeds the database, then runs the same Flamingock builder the application runs at startup (DEFERRED mode
 * lets the test seed first). A fresh Spring context per test gives a fresh builder.
 */
@SpringBootTest(classes = FlamingockMigrationIntegrationTest.App.class, properties = {
        "flamingock.management-mode=DEFERRED", "application.migrations.transactional=false" })
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class FlamingockMigrationIntegrationTest {
    static final String MONGOCK_COLLECTION = "mongockChangeLog";
    static final String FLAMINGOCK_COLLECTION = "flamingockAuditLog";
    static final String NEW_CHANGE_ID = "add-second-phone-to-personal-info";
    /** Change units deleted from the code base after they ran in production. */
    static final List<String> DELETED_CHANGE_IDS = List.of("add-thumb-for-each-upload",
            "add-thumb-for-each-upload-fixed");

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:8.0"));

    @SpringBootConfiguration
    @ImportAutoConfiguration({ MongoAutoConfiguration.class, DataMongoAutoConfiguration.class,
            FlamingockAutoConfiguration.class })
    @Import(MongoConfig.class)
    static class App {
        /** the jinja templates change needs the file service: not part of this test */
        @Bean
        JinjaTemplatesInstaller jinjaTemplatesInstaller() {
            return Mockito.mock(JinjaTemplatesInstaller.class);
        }
    }

    @Autowired
    MongoTemplate mongoTemplate;
    @Autowired
    AbstractChangeRunnerBuilder<?, ?> flamingock;
    @Autowired
    JinjaTemplatesInstaller jinjaTemplatesInstaller;

    /** Ids of the legacy Mongock change units still in the code base. */
    static List<String> legacyChangeIds() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(ChangeUnit.class));
        return scanner.findCandidateComponents("tech.artcoded.websitev2.changelogs").stream()
                .map(bd -> (String) ((AnnotatedBeanDefinition) bd).getMetadata()
                        .getAnnotationAttributes(ChangeUnit.class.getName()).get("id"))
                .toList();
    }

    /** A history entry as written by Mongock 5. */
    static Document mongockEntry(String changeId, String state, boolean systemChange) {
        return new Document("executionId", "2025-01-01T00:00:00-" + UUID.randomUUID()).append("changeId", changeId)
                .append("author", "Nordine Bittich").append("timestamp", new Date()).append("state", state)
                .append("type", "EXECUTION").append("changeLogClass", "tech.artcoded.websitev2.changelogs.X")
                .append("changeSetMethod", "execute").append("executionMillis", 12L)
                .append("executionHostname", "artcoded-prod").append("systemChange", systemChange);
    }

    /** What the production database looks like: every legacy change unit already ran with Mongock. */
    void seedProductionMongockHistory() {
        var history = new ArrayList<Document>();
        legacyChangeIds().forEach(id -> history.add(mongockEntry(id, "EXECUTED", false)));
        DELETED_CHANGE_IDS.forEach(id -> history.add(mongockEntry(id, "EXECUTED", false)));
        // mongock's own internal change
        history.add(mongockEntry("system-change-00001", "EXECUTED", true));
        mongoTemplate.getCollection(MONGOCK_COLLECTION).insertMany(history);
    }

    /**
     * The container is shared by the tests: start from an empty database, otherwise the audit of a previous test makes
     * Flamingock (rightly) skip the change.
     */
    @BeforeEach
    void emptyDatabase() {
        mongoTemplate.getDb().drop();
        // the mock is shared by the tests too: verify only the calls of the current one
        Mockito.reset(jinjaTemplatesInstaller);
    }

    void runMigrations() {
        flamingock.build().run();
    }

    long auditEntries(String changeId, String state) {
        return mongoTemplate.getCollection(FLAMINGOCK_COLLECTION)
                .countDocuments(new Document(KEY_CHANGE_ID, changeId).append(KEY_STATE, state));
    }

    @Test
    void allLegacyChangeUnitsAreFound() {
        var ids = legacyChangeIds();

        assertThat(ids).hasSize(60).doesNotHaveDuplicates().doesNotContainAnyElementsOf(DELETED_CHANGE_IDS);
    }

    @Test
    void productionUpgradeImportsTheMongockHistoryAndOnlyRunsTheNewChange() {
        seedProductionMongockHistory();
        var mongockHistorySize = mongoTemplate.getCollection(MONGOCK_COLLECTION).countDocuments();
        // personal info as stored today: no second phone
        mongoTemplate.save(PersonalInfo.builder().id("me").organizationName("ARTCODED")
                .organizationPhoneNumber("+32 470 00 00 00").build());
        mongoTemplate.getCollection("personalInfo").updateOne(new Document("_id", "me"),
                new Document("$unset", new Document(_0001__AddSecondPhoneToPersonalInfo.FIELD, "")));

        runMigrations();

        // every legacy change unit is known as applied: none re-ran (they need beans this context does not have,
        // running one would have failed the run)
        for (var id : legacyChangeIds()) {
            assertThat(auditEntries(id, "APPLIED")).as("imported history for %s", id).isPositive();
        }
        // the new change ran once
        assertThat(auditEntries(NEW_CHANGE_ID, "APPLIED")).isEqualTo(1);
        // the jinja templates were installed once
        assertThat(auditEntries("use-jinja-templates", "APPLIED")).isEqualTo(1);
        Mockito.verify(jinjaTemplatesInstaller).install();
        // the dossier report: its template and the monthly task
        assertThat(auditEntries("add-dossier-report", "APPLIED")).isEqualTo(1);
        // then its new versions, as new templates (_0004, _0005 and _0007: three times)
        assertThat(auditEntries("install-dossier-report-bugfix-template", "APPLIED")).isEqualTo(1);
        assertThat(auditEntries("add-yearly-dossier-report", "APPLIED")).isEqualTo(1);
        Mockito.verify(jinjaTemplatesInstaller, Mockito.times(3)).install(OaTemplateType.DOSSIER_REPORT);
        var reportTasks = mongoTemplate.find(_0004__AddDossierReport.reportTasks(), ReminderTask.class);
        assertThat(reportTasks).singleElement().satisfies(task -> {
            assertThat(task.getCronExpression()).isEqualTo(DossierReportAction.DEFAULT_CRON);
            assertThat(task.getNextDate()).isNotNull();
        });
        // the quarterly report task, without parameter
        assertThat(auditEntries("add-quarterly-dossier-report-task", "APPLIED")).isEqualTo(1);
        assertThat(mongoTemplate.find(_0006__AddQuarterlyDossierReportTask.quarterlyReportTasks(), ReminderTask.class))
                .singleElement().satisfies(task -> {
                    assertThat(task.getCronExpression()).isEqualTo(DossierQuarterlyReportAction.DEFAULT_CRON);
                    assertThat(task.getActionParameters()).isEmpty();
                });
        // the yearly report task, the 20th of january
        assertThat(mongoTemplate.find(_0007__AddYearlyDossierReport.yearlyReportTasks(), ReminderTask.class))
                .singleElement().satisfies(task -> {
                    assertThat(task.getCronExpression()).isEqualTo(DossierYearlyReportAction.DEFAULT_CRON);
                    assertThat(task.getActionParameters()).extracting("key").containsExactly("year");
                });
        var personalInfo = mongoTemplate.findById("me", PersonalInfo.class);
        assertThat(personalInfo).isNotNull();
        assertThat(personalInfo.getOrganizationSecondPhoneNumber()).isEmpty();
        assertThat(personalInfo.getOrganizationPhoneNumber()).isEqualTo("+32 470 00 00 00");
        // the stored document really has the field now (not only the java default)
        var raw = mongoTemplate.getCollection("personalInfo").find(new Document("_id", "me")).first();
        assertThat(raw).containsEntry(_0001__AddSecondPhoneToPersonalInfo.FIELD, "");
        // the administration menu link, for both roles
        assertThat(auditEntries("add-administration-menu", "APPLIED")).isEqualTo(1);
        assertThat(mongoTemplate.find(_0008__AddAdministrationMenu.administrationLinks(), MenuLink.class))
                .singleElement().satisfies(link -> {
                    assertThat(link.getTitle()).isEqualTo("Administration");
                    assertThat(link.getRoles()).containsExactlyInAnyOrder("ADMIN", "REGULATOR_OR_ACCOUNTANT");
                    assertThat(link.getRouterLinkActiveOptions().isExact()).isFalse();
                });
        // the old history is left as is
        assertThat(mongoTemplate.getCollection(MONGOCK_COLLECTION).countDocuments()).isEqualTo(mongockHistorySize);
    }

    @Test
    void theAdministrationMenuIsAddedAfterTheExistingLinks() {
        seedProductionMongockHistory();
        mongoTemplate.save(MenuLink.builder().title("Home").routerLink(new String[] { "" }).order(0).build());
        mongoTemplate.save(MenuLink.builder().title("Mails").routerLink(new String[] { "mails" }).order(21).build());

        runMigrations();

        assertThat(mongoTemplate.find(_0008__AddAdministrationMenu.administrationLinks(), MenuLink.class))
                .singleElement().extracting(MenuLink::getOrder).isEqualTo(22);
    }

    @Test
    void anExistingAdministrationMenuIsNotDuplicated() {
        seedProductionMongockHistory();
        mongoTemplate.save(MenuLink.builder().title("My admin").routerLink(new String[] { "administration" }).build());

        runMigrations();

        assertThat(mongoTemplate.find(_0008__AddAdministrationMenu.administrationLinks(), MenuLink.class))
                .singleElement().extracting(MenuLink::getTitle).isEqualTo("My admin");
        assertThat(auditEntries("add-administration-menu", "APPLIED")).isEqualTo(1);
    }

    @Test
    void anExistingSecondPhoneIsNeverOverwritten() {
        seedProductionMongockHistory();
        mongoTemplate.save(PersonalInfo.builder().id("me").organizationSecondPhoneNumber("+32 2 000 00 00").build());

        runMigrations();

        assertThat(mongoTemplate.findById("me", PersonalInfo.class).getOrganizationSecondPhoneNumber())
                .isEqualTo("+32 2 000 00 00");
        assertThat(auditEntries(NEW_CHANGE_ID, "APPLIED")).isEqualTo(1);
    }

    @Test
    void aChangeRecordedAsAppliedIsNeverRunAgain() {
        seedProductionMongockHistory();
        // the history says the change already ran (e.g. a previous deployment)
        mongoTemplate.getCollection(MONGOCK_COLLECTION).insertOne(mongockEntry(NEW_CHANGE_ID, "EXECUTED", false));
        mongoTemplate.getCollection("personalInfo").insertOne(new Document("_id", "me"));

        runMigrations();

        var raw = mongoTemplate.getCollection("personalInfo").find(new Document("_id", "me")).first();
        assertThat(raw).doesNotContainKey(_0001__AddSecondPhoneToPersonalInfo.FIELD);
        assertThat(auditEntries(NEW_CHANGE_ID, "APPLIED")).isEqualTo(1);
    }
}
