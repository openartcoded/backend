package tech.artcoded.websitev2.pages.fee;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tech.artcoded.event.v1.expense.*;
import tech.artcoded.websitev2.event.ExposedEventService;
import tech.artcoded.websitev2.upload.FileUploadService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

@Service
public class FeeService {
  private final FeeRepository feeRepository;
  private final LabelService labelService;
  private final FileUploadService fileUploadService;
  private final MongoTemplate mongoTemplate;
  private final ExposedEventService eventService;

  public FeeService(
      FeeRepository feeRepository,
      LabelService labelService, FileUploadService fileUploadService,
      MongoTemplate mongoTemplate, ExposedEventService eventService) {
    this.feeRepository = feeRepository;
    this.labelService = labelService;
    this.fileUploadService = fileUploadService;
    this.mongoTemplate = mongoTemplate;
    this.eventService = eventService;
  }

  @CacheEvict(cacheNames = "expenseSummary", allEntries = true)
  public void delete(String id) {
    Thread.startVirtualThread(
        () -> this.feeRepository
            .findById(id)
            .ifPresent(
                fee -> {
                  fee.getAttachmentIds().forEach(this.fileUploadService::delete);
                  this.feeRepository.delete(fee);
                  eventService.sendEvent(ExpenseRemoved.builder()
                      .expenseId(fee.getId())
                      .uploadIds(fee.getAttachmentIds())
                      .build());
                }));
  }

  public List<Fee> findAll() {
    return feeRepository.findByOrderByDateCreationDesc();
  }

  public Page<Fee> search(FeeSearchCriteria searchCriteria, Pageable pageable) {
    List<Criteria> criteriaList = new ArrayList<>();
    Criteria criteria = Criteria.where("archived").is(searchCriteria.isArchived());

    if (isNotEmpty(searchCriteria.getSubject())) {
      criteriaList.add(Criteria.where("subject").regex(".*%s.*".formatted(searchCriteria.getSubject()), "i"));
    }

    if (isNotEmpty(searchCriteria.getBody())) {
      criteriaList.add(Criteria.where("body").regex(".*%s.*".formatted(searchCriteria.getBody()), "i"));
    }
    if (searchCriteria.getDateBefore() != null) {
      criteriaList.add(Criteria.where("date").lt(searchCriteria.getDateBefore()));
    }

    if (searchCriteria.getDateAfter() != null) {
      criteriaList.add(Criteria.where("date").gt(searchCriteria.getDateAfter()));
    }

    if (isNotEmpty(searchCriteria.getId())) {
      criteriaList.add(Criteria.where("id").is(searchCriteria.getId()));
    }

    if (searchCriteria.getTag() != null) {
      criteriaList.add(Criteria.where("tag").is(searchCriteria.getTag()));
    }

    if (!criteriaList.isEmpty()) {
      criteria = criteria.andOperator(criteriaList.toArray(new Criteria[0]));
    }

    Query query = Query.query(criteria).with(pageable);

    long count = mongoTemplate.count(Query.query(criteria), Fee.class);
    List<Fee> fees = mongoTemplate.find(query, Fee.class);

    return PageableExecutionUtils.getPage(fees, pageable, () -> count);
  }

  @CacheEvict(cacheNames = "expenseSummary", allEntries = true)
  public Fee save(
      String subject, String body, Date date, List<MultipartFile> mockMultipartFiles) {

    var fee = Fee.builder()
        .subject(subject)
        .body(body)
        .date(date)
        .build();
    List<String> ids = mockMultipartFiles.stream()
        .map(mp -> fileUploadService.upload(mp, fee.getId(), date, false)).toList();
    Fee saved = this.feeRepository.save(fee.toBuilder().attachmentIds(ids).build());
    eventService.sendEvent(ExpenseReceived.builder()
        .expenseId(fee.getId())
        .uploadIds(saved.getAttachmentIds())
        .name(saved.getSubject())
        .build());
    return saved;
  }

  @CacheEvict(cacheNames = "expenseSummary", allEntries = true)
  public Fee update(Fee fee) {
    return this.feeRepository.save(fee.toBuilder().updatedDate(new Date()).build());
  }

  @CacheEvict(cacheNames = "expenseSummary", allEntries = true)
  public List<Fee> updateTag(String tag, List<String> feeIds) {
    Optional<Label> byTag = labelService.findByName(tag);
    return feeIds.stream()
        .map(this.feeRepository::findById)
        .flatMap(Optional::stream)
        .filter(Predicate.not(Fee::isArchived))
        .map(f -> f.toBuilder().tag(tag).updatedDate(new Date())
            .priceHVAT(f.getPriceHVAT() != null ? f.getPriceHVAT()
                : byTag.map(Label::getPriceHVAT)
                    .orElse(BigDecimal.ZERO))
            .vat(f.getVat() != null ? f.getVat()
                : byTag.map(Label::getVat)
                    .orElse(BigDecimal.ZERO))
            .build())
        .map(this.feeRepository::save)
        .peek(f -> eventService.sendEvent(ExpenseLabelUpdated.builder()
            .label(f.getTag())
            .expenseId(f.getId())
            .priceHVat(f.getPriceHVAT())
            .vat(f.getVat())
            .build()))
        .collect(Collectors.toList());
  }

  @CacheEvict(cacheNames = "expenseSummary", allEntries = true)
  public Optional<Fee> updatePrice(String feeId, BigDecimal priceHVat, BigDecimal vat) {
    return findById(feeId)
        .filter(Predicate.not(Fee::isArchived))
        .map(fee -> fee.toBuilder().priceHVAT(priceHVat).vat(vat).updatedDate(new Date()).build())
        .map(fee -> {
          var saved = feeRepository.save(fee);
          eventService.sendEvent(ExpensePriceUpdated.builder()
              .priceHVat(saved.getPriceHVAT())
              .vat(saved.getVat())
              .expenseId(saved.getId())
              .build());
          return saved;
        });
  }

  @CacheEvict(cacheNames = "expenseSummary", allEntries = true)
  public void removeAttachment(String feeId, String attachmentId) {
    Page<Fee> search = this.search(FeeSearchCriteria.builder().id(feeId).build(), Pageable.unpaged());
    search.stream().findFirst().ifPresent(f -> {
      if (f.getAttachmentIds().stream().anyMatch(attachmentId::equals)) {
        fileUploadService.delete(attachmentId);
        var updated = this.feeRepository.save(f.toBuilder()
            .updatedDate(new Date())
            .attachmentIds(f.getAttachmentIds()
                .stream()
                .filter(Predicate.not(attachmentId::equals))
                .collect(Collectors.toList()))
            .build());
        eventService.sendEvent(ExpenseAttachmentRemoved.builder()
            .uploadId(attachmentId)
            .expenseId(updated.getId())
            .build());
      }
    });
  }

  public Optional<Fee> findById(String feeId) {
    return feeRepository.findById(feeId);
  }

  public List<Fee> findAll(Collection<String> ids) {
    Iterable<Fee> it = feeRepository.findAllById(ids);
    List<Fee> results = new ArrayList<>();
    it.forEach(results::add);
    return results;
  }

  @CachePut(cacheNames = "expenseSummary", key = "'expSummary'")
  public List<FeeSummary> getSummaries() {
    var expenses = this.search(FeeSearchCriteria.builder()
        .archived(true)
        .build(), Pageable.unpaged());
    var groupByLabel = expenses.get().collect(Collectors.groupingBy(Fee::getTag));

    return groupByLabel.entrySet().stream().map(e -> e.getValue().stream()
        .map(f -> FeeSummary.builder()
            .totalHVAT(f.getPriceHVAT())
            .totalVAT(f.getVat())
            .tag(f.getTag())
            .build())
        .reduce(FeeSummary.builder()
            .tag(e.getKey())
            .build(),
            (feeSummary, feeSummary2) -> feeSummary.toBuilder()
                .totalVAT(feeSummary.getTotalVAT()
                    .add(feeSummary2.getTotalVAT()))
                .totalHVAT(feeSummary.getTotalHVAT()
                    .add(feeSummary2.getTotalHVAT()))
                .build()))

        .toList();
  }

}
