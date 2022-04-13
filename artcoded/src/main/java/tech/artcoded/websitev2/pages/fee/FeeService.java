package tech.artcoded.websitev2.pages.fee;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tech.artcoded.websitev2.api.helper.IdGenerators;
import tech.artcoded.websitev2.upload.FileUploadService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

@Service
@Slf4j
public class FeeService {
  private final FeeRepository feeRepository;
  private final LabelService labelService;
  private final FileUploadService fileUploadService;
  private final MongoTemplate mongoTemplate;

  public FeeService(
          FeeRepository feeRepository,
          LabelService labelService, FileUploadService fileUploadService,
          MongoTemplate mongoTemplate) {
    this.feeRepository = feeRepository;
    this.labelService = labelService;
    this.fileUploadService = fileUploadService;
    this.mongoTemplate = mongoTemplate;
  }

  public void delete(String id) {
    CompletableFuture.runAsync(
            () -> this.feeRepository
                    .findById(id)
                    .ifPresent(
                            fee -> {
                              fee.getAttachmentIds().forEach(this.fileUploadService::delete);
                              this.feeRepository.delete(fee);
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

  public Fee save(
          String subject, String body, Date date, List<MultipartFile> mockMultipartFiles) {
    String id = IdGenerators.get();
    List<String> ids =
            mockMultipartFiles.stream()
                              .map(mp -> fileUploadService.upload(mp, id, false))
                              .collect(Collectors.toList());
    var fee =
            Fee.builder()
               .id(id)
               .subject(subject)
               .body(body)
               .date(date)
               .attachmentIds(ids)
               .build();
    return this.feeRepository.save(fee);
  }

  public List<Fee> updateTag(String tag, List<String> feeIds) {
    Optional<Label> byTag = labelService.findByName(tag);
    return feeIds.stream()
                 .map(this.feeRepository::findById)
                 .flatMap(Optional::stream)
                 .filter(Predicate.not(Fee::isArchived))
                 .map(f ->
                              f.toBuilder().tag(tag).updatedDate(new Date())
                               .priceHVAT(f.getPriceHVAT() != null ? f.getPriceHVAT() : byTag.map(Label::getPriceHVAT)
                                                                                             .orElse(BigDecimal.ZERO))
                               .vat(f.getVat() != null ? f.getVat() : byTag.map(Label::getVat)
                                                                           .orElse(BigDecimal.ZERO))
                               .build()
                 )
                 .map(this.feeRepository::save)
                 .collect(Collectors.toList());
  }

  public Optional<Fee> updatePrice(String feeId, BigDecimal priceHVat, BigDecimal vat) {
    return findById(feeId)
            .filter(Predicate.not(Fee::isArchived))
            .map(fee -> fee.toBuilder().priceHVAT(priceHVat).vat(vat).updatedDate(new Date()).build())
            .map(feeRepository::save);
  }

  public void removeAttachment(String feeId, String attachmentId) {
    Page<Fee> search = this.search(FeeSearchCriteria.builder().id(feeId).build(), Pageable.unpaged());
    search.stream().findFirst().ifPresent(f -> {
      if (f.getAttachmentIds().stream().anyMatch(attachmentId::equals)) {
        fileUploadService.delete(attachmentId);
        this.feeRepository.save(f.toBuilder()
                                 .updatedDate(new Date())
                                 .attachmentIds(f.getAttachmentIds()
                                                 .stream()
                                                 .filter(Predicate.not(attachmentId::equals))
                                                 .collect(Collectors.toList())).build());
      }
    });
  }

  public Optional<Fee> findById(String feeId) {
    return feeRepository.findById(feeId);
  }


}
