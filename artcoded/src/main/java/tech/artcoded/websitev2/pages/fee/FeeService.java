package tech.artcoded.websitev2.pages.fee;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
import java.util.stream.Stream;

@Service
@Slf4j
public class FeeService {
  private final FeeRepository feeRepository;
  private final DefaultPriceForTagRepository defaultPriceForTagRepository;
  private final FileUploadService fileUploadService;
  private final MongoTemplate mongoTemplate;

  public FeeService(
          FeeRepository feeRepository,
          DefaultPriceForTagRepository defaultPriceForTagRepository, FileUploadService fileUploadService,
          MongoTemplate mongoTemplate) {
    this.feeRepository = feeRepository;
    this.defaultPriceForTagRepository = defaultPriceForTagRepository;
    this.fileUploadService = fileUploadService;
    this.mongoTemplate = mongoTemplate;
  }

  public void delete(String id) {
    CompletableFuture.runAsync(
            () -> {
              this.feeRepository
                      .findById(id)
                      .ifPresent(
                              fee -> {
                                fee.getAttachmentIds().forEach(this.fileUploadService::delete);
                                this.feeRepository.delete(fee);
                              });
            });
  }

  public List<Fee> findAll() {
    return feeRepository.findByOrderByDateCreationDesc();
  }

  public Page<Fee> search(FeeSearchCriteria searchCriteria, Pageable pageable) {
    List<Criteria> criteriaList = new ArrayList<>();
    Criteria criteria = Criteria.where("archived").is(searchCriteria.isArchived());

    if (StringUtils.isNotEmpty(searchCriteria.getSubject())) {
      criteriaList.add(Criteria.where("subject").regex(".*%s.*".formatted(searchCriteria.getSubject()), "i"));
    }

    if (StringUtils.isNotEmpty(searchCriteria.getBody())) {
      criteriaList.add(Criteria.where("body").regex(".*%s.*".formatted(searchCriteria.getBody()), "i"));
    }
    if (searchCriteria.getDatebefore() != null) {
      criteriaList.add(Criteria.where("date").lt(searchCriteria.getDatebefore()));
    }

    if (searchCriteria.getDateAfter() != null) {
      criteriaList.add(Criteria.where("date").gt(searchCriteria.getDateAfter()));
    }

    if (StringUtils.isNotEmpty(searchCriteria.getId())) {
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

  public List<Fee> updateTag(Tag tag, List<String> feeIds) {
    Optional<DefaultPriceForTag> byTag = defaultPriceForTagRepository.findByTag(tag);
    return feeIds.stream()
                 .map(this.feeRepository::findById)
                 .flatMap(Optional::stream)
                 .filter(Predicate.not(Fee::isArchived))
                 .map(f ->
                              f.toBuilder().tag(tag).updatedDate(new Date())
                               .priceHVAT(f.getPriceHVAT() != null ? f.getPriceHVAT() : byTag.map(DefaultPriceForTag::getPriceHVAT)
                                                                                             .orElse(BigDecimal.ZERO))
                               .vat(f.getVat() != null ? f.getVat() : byTag.map(DefaultPriceForTag::getVat)
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

  public List<DefaultPriceForTag> findAllDefaultPriceForTag() {
    List<DefaultPriceForTag> defaultPriceForTags = defaultPriceForTagRepository.findByTagIsNotIn(List.of(Tag.OTHER, Tag.OIL));
    if (defaultPriceForTags.isEmpty()) {
      return Stream.of(Tag.INTERNET, Tag.GSM, Tag.ACCOUNTING, Tag.LEASING)
                   .map(tag -> DefaultPriceForTag.builder().tag(tag).build())
                   .map(defaultPriceForTagRepository::save)
                   .collect(Collectors.toUnmodifiableList());
    }
    return defaultPriceForTags;
  }

  public void updateDefaultPriceForTag(List<DefaultPriceForTag> defaultPriceForTagList) {
    defaultPriceForTagList.forEach(defaultPriceForTag -> {
      defaultPriceForTagRepository.findById(defaultPriceForTag.getId())
                                  .map(price -> price.toBuilder()
                                                     .priceHVAT(defaultPriceForTag.getPriceHVAT())
                                                     .vat(defaultPriceForTag.getVat())
                                                     .updatedDate(new Date())
                                                     .build())
                                  .ifPresent(defaultPriceForTagRepository::save);
    });
  }

}
