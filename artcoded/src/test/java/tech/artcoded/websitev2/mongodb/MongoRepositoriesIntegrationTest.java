package tech.artcoded.websitev2.mongodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import tech.artcoded.websitev2.event.ExposedEventService;
import tech.artcoded.websitev2.pages.dossier.Dossier;
import tech.artcoded.websitev2.pages.dossier.DossierRepository;
import tech.artcoded.websitev2.pages.fee.Fee;
import tech.artcoded.websitev2.pages.fee.FeeRepository;
import tech.artcoded.websitev2.pages.fee.FeeSearchCriteria;
import tech.artcoded.websitev2.pages.fee.FeeService;
import tech.artcoded.websitev2.pages.fee.LabelService;
import tech.artcoded.websitev2.pages.report.Post;
import tech.artcoded.websitev2.pages.report.Post.PostStatus;
import tech.artcoded.websitev2.pages.report.PostRepository;
import tech.artcoded.websitev2.pages.report.PostTag;
import tech.artcoded.websitev2.pages.report.PostTagRepository;
import tech.artcoded.websitev2.pages.report.TagCleanupAction;
import tech.artcoded.websitev2.pages.timesheet.Timesheet;
import tech.artcoded.websitev2.pages.timesheet.TimesheetRepository;
import tech.artcoded.websitev2.upload.IFileUploadService;
import tech.artcoded.websitev2.utils.helper.DateHelper;

/**
 * Derived queries, criteria queries and custom converters against a real MongoDB (Testcontainers). Skipped when no
 * docker daemon is available.
 */
@DataMongoTest
@Testcontainers(disabledWithoutDocker = true)
class MongoRepositoriesIntegrationTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:8.0"));

    /** Only the repositories under test and the application mongo config (custom converters). */
    @Configuration
    @EnableMongoRepositories(basePackageClasses = { PostRepository.class, DossierRepository.class,
            FeeRepository.class, TimesheetRepository.class })
    @Import(MongoConfig.class)
    static class Config {
    }

    @Autowired
    MongoTemplate mongoTemplate;
    @Autowired
    PostRepository postRepository;
    @Autowired
    PostTagRepository postTagRepository;
    @Autowired
    DossierRepository dossierRepository;
    @Autowired
    FeeRepository feeRepository;
    @Autowired
    TimesheetRepository timesheetRepository;

    @BeforeEach
    void cleanDatabase() {
        mongoTemplate.getDb().drop();
    }

    private static Date daysAgo(int days) {
        return DateHelper.toDate(LocalDate.now().minusDays(days));
    }

    // ----------------------------------------------------------------- posts

    @Test
    void postsAreFoundByTagAndStatus() {
        postRepository.saveAll(List.of(
                Post.builder().id("p1").title("a").tags(Set.of("java", "spring")).status(PostStatus.DONE)
                        .coverId("cover").updatedDate(daysAgo(2)).build(),
                Post.builder().id("p2").title("b").tags(Set.of("java")).status(PostStatus.DONE).coverId(null)
                        .updatedDate(daysAgo(1)).build(),
                Post.builder().id("p3").title("c").tags(Set.of()).status(PostStatus.DRAFT).coverId("cover")
                        .updatedDate(daysAgo(0)).build()));

        assertThat(postRepository.existsByTags("spring")).isTrue();
        assertThat(postRepository.existsByTags("cobol")).isFalse();
        assertThat(postRepository.findByStatusIsAndCoverIdIsNotNullOrderByUpdatedDateDesc(PostStatus.DONE,
                Pageable.unpaged())).extracting(Post::getId).containsExactly("p1");
        assertThat(postRepository.findByOrderByUpdatedDateDesc(PageRequest.of(0, 2))).extracting(Post::getId)
                .containsExactly("p3", "p2");
    }

    @Test
    void bookmarkedPostsComeMostRecentlyBookmarkedFirst() {
        postRepository.saveAll(List.of(Post.builder().id("old").bookmarked(true).bookmarkedDate(daysAgo(5)).build(),
                Post.builder().id("recent").bookmarked(true).bookmarkedDate(daysAgo(1)).build(),
                Post.builder().id("not").bookmarked(false).build()));

        var page = postRepository.findByBookmarkedIsOrderByBookmarkedDateDesc(true, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page).extracting(Post::getId).containsExactly("recent", "old");
    }

    @Test
    void tagCleanupRemovesOnlyUnusedTags() {
        postRepository.save(Post.builder().id("p1").tags(Set.of("java")).build());
        postTagRepository.saveAll(List.of(new PostTag("java"), new PostTag("cobol"), new PostTag("perl")));
        var action = new TagCleanupAction(postTagRepository, postRepository);

        assertThat(action.shouldNotRun(List.of())).isFalse();
        var result = action.run(List.of());

        assertThat(result.getMessages()).contains("deleting unused tag cobol", "deleting unused tag perl");
        assertThat(postTagRepository.findAll()).extracting(PostTag::getTag).containsExactly("java");
        assertThat(action.shouldNotRun(List.of())).isTrue();
    }

    // ----------------------------------------------------------------- dossiers

    @Test
    void dossierQueries() {
        dossierRepository.saveAll(List.of(
                Dossier.builder().id("open").name("open").closed(false).feeIds(Set.of("f1", "f2")).build(),
                Dossier.builder().id("closed-old").closed(true).creationDate(daysAgo(100))
                        .backupDate(new Date()).build(),
                Dossier.builder().id("closed-new").closed(true).creationDate(daysAgo(10)).backupDate(null).build()));

        assertThat(dossierRepository.findOneByClosedIsFalse()).map(Dossier::getId).contains("open");
        assertThat(dossierRepository.findOneByFeeIdsIsContaining("f2")).map(Dossier::getId).contains("open");
        assertThat(dossierRepository.findOneByFeeIdsIsContaining("f3")).isEmpty();
        assertThat(dossierRepository.findFirstByClosedIsTrueOrderByCreationDateDesc()).map(Dossier::getId)
                .contains("closed-new");
        assertThat(dossierRepository.findByClosedIsTrueAndBackupDateIsNull()).extracting(Dossier::getId)
                .containsExactly("closed-new");
        assertThat(dossierRepository.findOneByClosedIsTrueAndIdIs("open")).isEmpty();
        assertThat(dossierRepository.findByClosedIs(true, PageRequest.of(0, 10)).getTotalElements()).isEqualTo(2);
    }

    @Test
    void dossierAmountsSurviveARoundTrip() {
        dossierRepository.save(Dossier.builder().id("d").tvaDue(new BigDecimal("1234.56")).build());

        var reloaded = dossierRepository.findById("d").orElseThrow();

        assertThat(reloaded.getTvaDue()).isEqualByComparingTo("1234.56");
        assertThat(reloaded.getTvaToBePaid()).isEqualByComparingTo("-1234.56");
    }

    // ----------------------------------------------------------------- expenses

    private FeeService feeService() {
        return new FeeService(feeRepository, mock(LabelService.class), mock(IFileUploadService.class), mongoTemplate,
                mock(ExposedEventService.class));
    }

    @Test
    void expenseSearchTreatsUserInputLiterally() {
        feeRepository.saveAll(List.of(Fee.builder().id("f1").subject("Lunch (client A)").build(),
                Fee.builder().id("f2").subject("Lunch client B").build(),
                Fee.builder().id("f3").subject("Train *1st class*").build()));
        var service = feeService();

        // used to be a regex: "(" was an invalid pattern and "." / "*" matched anything
        assertThat(service.search(FeeSearchCriteria.builder().subject("(client").build(), Pageable.unpaged()))
                .extracting(Fee::getId).containsExactly("f1");
        assertThat(service.search(FeeSearchCriteria.builder().subject("*1st").build(), Pageable.unpaged()))
                .extracting(Fee::getId).containsExactly("f3");
        assertThat(service.search(FeeSearchCriteria.builder().subject(".*").build(), Pageable.unpaged())).isEmpty();
        // still case-insensitive "contains"
        assertThat(service.search(FeeSearchCriteria.builder().subject("LUNCH").build(), Pageable.unpaged()))
                .extracting(Fee::getId).containsExactlyInAnyOrder("f1", "f2");
    }

    @Test
    void expenseSearchFilters() {
        feeRepository.saveAll(List.of(
                Fee.builder().id("inbox").subject("a").date(daysAgo(1)).build(),
                Fee.builder().id("archived-proof").subject("b").archived(true).tag("TRAIN").date(daysAgo(20))
                        .paymentProofUploadId("proof").build(),
                Fee.builder().id("archived-no-proof").subject("c").archived(true).tag("FOOD").date(daysAgo(40))
                        .build(),
                Fee.builder().id("bookmarked").subject("d").archived(true).bookmarked(true).build()));
        var service = feeService();

        assertThat(service.search(FeeSearchCriteria.builder().build(), Pageable.unpaged())).extracting(Fee::getId)
                .containsExactly("inbox");
        assertThat(service.search(FeeSearchCriteria.builder().archived(true).build(), Pageable.unpaged()))
                .extracting(Fee::getId)
                .containsExactlyInAnyOrder("archived-proof", "archived-no-proof", "bookmarked");
        assertThat(service.search(FeeSearchCriteria.builder().archived(true).hasPaymentProof(true).build(),
                Pageable.unpaged())).extracting(Fee::getId).containsExactly("archived-proof");
        assertThat(service.search(FeeSearchCriteria.builder().archived(true).tag("FOOD").build(),
                Pageable.unpaged())).extracting(Fee::getId).containsExactly("archived-no-proof");
        assertThat(service.search(FeeSearchCriteria.builder().archived(true).dateAfter(daysAgo(30)).build(),
                Pageable.unpaged())).extracting(Fee::getId).containsExactly("archived-proof");
        assertThat(service.search(FeeSearchCriteria.builder().bookmarked(true).build(), Pageable.unpaged()))
                .extracting(Fee::getId).containsExactly("bookmarked");
        assertThat(service.search(FeeSearchCriteria.builder().archived(true).id("archived-proof").build(),
                Pageable.unpaged())).extracting(Fee::getId).containsExactly("archived-proof");
    }

    @Test
    void expenseSearchIsPaged() {
        for (int i = 0; i < 7; i++) {
            feeRepository.save(Fee.builder().id("f" + i).subject("expense " + i).build());
        }

        var page = feeService().search(FeeSearchCriteria.builder().build(), PageRequest.of(1, 3));

        assertThat(page.getTotalElements()).isEqualTo(7);
        assertThat(page.getTotalPages()).isEqualTo(3);
        assertThat(page.getContent()).hasSize(3);
    }

    // ----------------------------------------------------------------- custom converters

    @Test
    void yearMonthIsStoredAsADateAndCanBeQueried() {
        timesheetRepository.saveAll(List.of(Timesheet.builder().id("jan").name("2026-01").yearMonth(YearMonth.of(2026, 1)).build(),
                Timesheet.builder().id("dec").name("2025-12").yearMonth(YearMonth.of(2025, 12)).build(),
                Timesheet.builder().id("feb").name("2026-02").yearMonth(YearMonth.of(2026, 2)).build()));

        // written by YearMonthToLocalDateConverter as the first day of the month
        Document raw = mongoTemplate.getCollection(mongoTemplate.getCollectionName(Timesheet.class))
                .find(new Document("_id", "jan")).first();
        assertThat(raw).isNotNull();
        assertThat(raw.get("yearMonth")).isInstanceOf(Date.class);
        assertThat(DateHelper.toLocalDate(raw.getDate("yearMonth"))).isEqualTo(LocalDate.of(2026, 1, 1));

        // read back by DateToYearMonthConverter
        assertThat(timesheetRepository.findById("jan")).map(Timesheet::getYearMonth).contains(YearMonth.of(2026, 1));
        assertThat(timesheetRepository.findByYearMonth(YearMonth.of(2026, 2))).extracting(Timesheet::getId)
                .containsExactly("feb");
        assertThat(timesheetRepository.findByOrderByYearMonthDesc(Pageable.unpaged())).extracting(Timesheet::getId)
                .containsExactly("feb", "jan", "dec");
    }
}
