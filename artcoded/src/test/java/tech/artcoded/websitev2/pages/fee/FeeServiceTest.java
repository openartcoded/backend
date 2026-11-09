package tech.artcoded.websitev2.pages.fee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import tech.artcoded.event.IEvent;
import tech.artcoded.event.v1.expense.ExpenseAttachmentRemoved;
import tech.artcoded.event.v1.expense.ExpenseLabelUpdated;
import tech.artcoded.event.v1.expense.ExpensePriceUpdated;
import tech.artcoded.event.v1.expense.ExpenseReceived;
import tech.artcoded.event.v1.expense.ExpenseRemoved;
import tech.artcoded.websitev2.event.ExposedEventService;
import tech.artcoded.websitev2.upload.IFileUploadService;

class FeeServiceTest {
    private FeeRepository repository;
    private LabelService labelService;
    private IFileUploadService uploadService;
    private MongoTemplate mongoTemplate;
    private ExposedEventService eventService;
    private FeeService service;

    @BeforeEach
    void setUp() {
        repository = mock(FeeRepository.class);
        labelService = mock(LabelService.class);
        uploadService = mock(IFileUploadService.class);
        mongoTemplate = mock(MongoTemplate.class);
        eventService = mock(ExposedEventService.class);
        service = new FeeService(repository, labelService, uploadService, mongoTemplate, eventService);
        when(repository.save(any(Fee.class))).thenAnswer(a -> a.getArgument(0));
    }

    private Document searchQuery(FeeSearchCriteria criteria) {
        when(mongoTemplate.find(any(Query.class), eq(Fee.class))).thenReturn(List.of());
        service.search(criteria, PageRequest.of(0, 10));
        var captor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(captor.capture(), eq(Fee.class));
        return captor.getValue().getQueryObject();
    }

    @ParameterizedTest
    @ValueSource(strings = { "(", "a.b", "50% [draft]", "c++", "\\", "x|y", "^$" })
    void searchMatchesUserInputLiterally(String input) {
        var regex = FeeService.containsIgnoreCase(input);
        var pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        assertThat(pattern.matcher("before " + input + " after").matches()).isTrue();
        assertThat(pattern.matcher("unrelated").matches()).isFalse();
    }

    @Test
    void dotInSearchIsNotAWildcard() {
        var pattern = Pattern.compile(FeeService.containsIgnoreCase("a.b"));
        assertThat(pattern.matcher("axb").matches()).isFalse();
        assertThat(pattern.matcher("a.b").matches()).isTrue();
    }

    @Test
    void searchByDefaultReturnsNonArchivedExpenses() {
        var query = searchQuery(FeeSearchCriteria.builder().build());
        assertThat(query).containsEntry("archived", false);
    }

    @Test
    void bookmarkedSearchIgnoresArchivedFlag() {
        var query = searchQuery(FeeSearchCriteria.builder().bookmarked(true).archived(true).build());
        assertThat(query).containsEntry("bookmarked", true).doesNotContainKey("archived");
    }

    @Test
    void searchCombinesAllCriteria() {
        var query = searchQuery(FeeSearchCriteria.builder().archived(true).subject("(invoice").body("body")
                .tag("TRAVEL").id("fee-1").dateAfter(new Date(0)).dateBefore(new Date()).hasPaymentProof(true)
                .build());
        var json = query.toJson();
        assertThat(json).contains("\"archived\": true").contains("TRAVEL").contains("fee-1")
                .contains("paymentProofUploadId").contains("\\\\(invoice");
    }

    @Test
    void searchWithoutPaymentProof() {
        var json = searchQuery(FeeSearchCriteria.builder().hasPaymentProof(false).build()).toJson();
        assertThat(json).contains("\"paymentProofUploadId\": null");
    }

    @Test
    void searchReturnsAPageWithTheTotalCount() {
        var fee = Fee.builder().subject("x").build();
        when(mongoTemplate.find(any(Query.class), eq(Fee.class))).thenReturn(List.of(fee));
        when(mongoTemplate.count(any(Query.class), eq(Fee.class))).thenReturn(42L);
        var page = service.search(FeeSearchCriteria.builder().build(), PageRequest.of(0, 1));
        assertThat(page.getContent()).containsExactly(fee);
        assertThat(page.getTotalElements()).isEqualTo(42);
    }

    @Test
    void saveUploadsAttachmentsAndPublishesEvent() {
        when(uploadService.uploadAll(anyList(), anyString(), anyBoolean())).thenReturn(List.of("u1", "u2"));
        var saved = service.save("subject", "body", new Date(),
                List.of(new MockMultipartFile("a", new byte[] { 1 }), new MockMultipartFile("b", new byte[] { 2 })));
        assertThat(saved.getAttachmentIds()).containsExactly("u1", "u2");
        var captor = ArgumentCaptor.forClass(IEvent.class);
        verify(eventService).sendEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOfSatisfying(ExpenseReceived.class, e -> {
            assertThat(e.getExpenseId()).isEqualTo(saved.getId());
            assertThat(e.getName()).isEqualTo("subject");
            assertThat(e.getUploadIds()).containsExactly("u1", "u2");
        });
    }

    @Test
    void addAttachmentAsPaymentProof() {
        when(repository.findById("f1"))
                .thenReturn(Optional.of(Fee.builder().id("f1").attachmentIds(null).build()));
        when(uploadService.upload(any(MultipartFile.class), eq("f1"), anyBoolean())).thenReturn("proof");
        var fee = service.addAttachment("f1", new MockMultipartFile("p", new byte[] { 1 }), true);
        assertThat(fee.getAttachmentIds()).containsExactly("proof");
        assertThat(fee.getPaymentProofUploadId()).isEqualTo("proof");
    }

    @Test
    void deleteRemovesAttachmentsAsynchronously() {
        var fee = Fee.builder().id("f1").attachmentIds(List.of("a", "b")).build();
        when(repository.findById("f1")).thenReturn(Optional.of(fee));
        service.delete("f1");
        verify(uploadService, timeout(2000)).delete("a");
        verify(uploadService, timeout(2000)).delete("b");
        verify(repository, timeout(2000)).delete(fee);
        var captor = ArgumentCaptor.forClass(IEvent.class);
        verify(eventService, timeout(2000)).sendEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(ExpenseRemoved.class);
    }

    @Test
    void deleteExpenseWithoutAttachments() {
        var fee = Fee.builder().id("f1").attachmentIds(null).build();
        when(repository.findById("f1")).thenReturn(Optional.of(fee));
        service.delete("f1");
        verify(repository, timeout(2000)).delete(fee);
    }

    @Test
    void updateTagAppliesLabelDefaultPricesAndSkipsArchivedExpenses() {
        var noPrice = Fee.builder().id("f1").build();
        var withPrice = Fee.builder().id("f2").priceHVAT(new BigDecimal("10")).vat(new BigDecimal("2.1")).build();
        var archived = Fee.builder().id("f3").archived(true).build();
        when(repository.findById("f1")).thenReturn(Optional.of(noPrice));
        when(repository.findById("f2")).thenReturn(Optional.of(withPrice));
        when(repository.findById("f3")).thenReturn(Optional.of(archived));
        when(labelService.findByName("TRAVEL")).thenReturn(Optional.of(
                Label.builder().name("TRAVEL").priceHVAT(new BigDecimal("100")).vat(new BigDecimal("21")).build()));

        var updated = service.updateTag("TRAVEL", List.of("f1", "f2", "f3", "missing"));

        assertThat(updated).extracting(Fee::getId).containsExactly("f1", "f2");
        assertThat(updated).allMatch(f -> "TRAVEL".equals(f.getTag()));
        assertThat(updated.get(0).getPriceHVAT()).isEqualByComparingTo("100");
        assertThat(updated.get(0).getVat()).isEqualByComparingTo("21");
        assertThat(updated.get(1).getPriceHVAT()).isEqualByComparingTo("10");
        assertThat(updated.get(1).getVat()).isEqualByComparingTo("2.1");
        var captor = ArgumentCaptor.forClass(IEvent.class);
        verify(eventService, org.mockito.Mockito.times(2)).sendEvent(captor.capture());
        assertThat(captor.getAllValues()).allMatch(ExpenseLabelUpdated.class::isInstance);
    }

    @Test
    void updateTagWithUnknownLabelDefaultsToZero() {
        when(repository.findById("f1")).thenReturn(Optional.of(Fee.builder().id("f1").build()));
        var updated = service.updateTag("UNKNOWN", List.of("f1"));
        assertThat(updated.getFirst().getPriceHVAT()).isZero();
        assertThat(updated.getFirst().getVat()).isZero();
    }

    @Test
    void priceOfArchivedExpenseCannotChange() {
        when(repository.findById("archived")).thenReturn(Optional.of(Fee.builder().archived(true).build()));
        assertThat(service.updatePrice("archived", BigDecimal.ONE, BigDecimal.ONE)).isEmpty();
        verify(repository, never()).save(any());

        when(repository.findById("f1")).thenReturn(Optional.of(Fee.builder().id("f1").build()));
        var updated = service.updatePrice("f1", new BigDecimal("10"), new BigDecimal("2.1"));
        assertThat(updated).isPresent();
        assertThat(updated.get().getPriceTot()).isEqualByComparingTo("12.1");
        var captor = ArgumentCaptor.forClass(IEvent.class);
        verify(eventService).sendEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(ExpensePriceUpdated.class);
    }

    @Test
    void removeAttachmentOnlyWhenItBelongsToTheExpense() {
        var fee = Fee.builder().id("f1").attachmentIds(new ArrayList<>(List.of("a", "b"))).build();
        when(mongoTemplate.find(any(Query.class), eq(Fee.class))).thenReturn(List.of(fee));

        service.removeAttachment("f1", "other");
        verify(uploadService, never()).delete(anyString());

        service.removeAttachment("f1", "a");
        verify(uploadService).delete("a");
        var saved = ArgumentCaptor.forClass(Fee.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getAttachmentIds()).containsExactly("b");
        var event = ArgumentCaptor.forClass(IEvent.class);
        verify(eventService).sendEvent(event.capture());
        assertThat(event.getValue()).isInstanceOf(ExpenseAttachmentRemoved.class);
    }

    @Test
    void toggleBookmarked() {
        when(repository.findById("f1")).thenReturn(Optional.of(Fee.builder().id("f1").bookmarked(false).build()));
        var bookmarked = service.toggleBookmarked("f1").orElseThrow();
        assertThat(bookmarked.isBookmarked()).isTrue();
        assertThat(bookmarked.getBookmarkedDate()).isNotNull();

        when(repository.findById("f1")).thenReturn(Optional.of(bookmarked));
        var unbookmarked = service.toggleBookmarked("f1").orElseThrow();
        assertThat(unbookmarked.isBookmarked()).isFalse();
        assertThat(unbookmarked.getBookmarkedDate()).isNull();
    }

    @Test
    void summariesGroupArchivedExpensesByLabelAndTolerateMissingData() {
        var fees = List.of(
                Fee.builder().tag("TRAVEL").priceHVAT(new BigDecimal("10")).vat(new BigDecimal("2.1")).build(),
                Fee.builder().tag("TRAVEL").priceHVAT(new BigDecimal("5")).vat(null).build(),
                Fee.builder().tag("FOOD").priceHVAT(null).vat(new BigDecimal("1")).build(),
                // no label: used to make Collectors.groupingBy throw a NullPointerException
                Fee.builder().tag(null).priceHVAT(new BigDecimal("1000")).build());
        when(mongoTemplate.find(any(Query.class), eq(Fee.class))).thenReturn(fees);

        var summaries = service.getSummaries();

        assertThat(summaries).hasSize(2);
        var travel = summaries.stream().filter(s -> "TRAVEL".equals(s.getTag())).findFirst().orElseThrow();
        assertThat(travel.getTotalHVAT()).isEqualByComparingTo("15");
        assertThat(travel.getTotalVAT()).isEqualByComparingTo("2.1");
        var food = summaries.stream().filter(s -> "FOOD".equals(s.getTag())).findFirst().orElseThrow();
        assertThat(food.getTotalHVAT()).isZero();
        assertThat(food.getTotalVAT()).isEqualByComparingTo("1");
    }

    @Test
    void priceTotalIgnoresMissingParts() {
        assertThat(Fee.builder().build().getPriceTot()).isZero();
        assertThat(Fee.builder().priceHVAT(BigDecimal.TEN).build().getPriceTot()).isEqualByComparingTo("10");
    }

    @Test
    void oldIdsAreReplacedInAttachmentsAndPaymentProof() {
        var fee = Fee.builder().id("f1").attachmentIds(List.of("old", "x")).paymentProofUploadId("old").build();
        when(repository.findById("f1")).thenReturn(Optional.of(fee));
        service.updateOldId("f1", "old", "new");
        var captor = ArgumentCaptor.forClass(Fee.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getAttachmentIds()).containsExactlyInAnyOrder("x", "new");
        assertThat(captor.getValue().getPaymentProofUploadId()).isEqualTo("new");
    }

    @Test
    void findAllByIdsCopiesTheIterable() {
        var fee = Fee.builder().id("f1").build();
        when(repository.findAllById(List.of("f1"))).thenReturn(List.of(fee));
        assertThat(service.findAll(List.of("f1"))).containsExactly(fee);
        assertThat(service.getCorrelationLabels(List.of("f1"))).containsEntry("f1", "Expense 'null'");
    }

    @Test
    void unpagedSearchIsSupported() {
        when(mongoTemplate.find(any(Query.class), eq(Fee.class))).thenReturn(List.of());
        assertThat(service.search(FeeSearchCriteria.builder().build(), Pageable.unpaged()).getContent()).isEmpty();
    }
}
