package tech.artcoded.websitev2.pages.document;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tech.artcoded.websitev2.api.helper.IdGenerators;
import tech.artcoded.websitev2.notification.NotificationService;
import tech.artcoded.websitev2.upload.FileUploadService;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static java.util.Optional.ofNullable;

@Service
@Slf4j
public class AdministrativeDocumentService {
  private static final String ADMINISTRATIVE_DOCUMENT_ADDED = "ADMINISTRATIVE_DOCUMENT_ADDED";
  private static final String ADMINISTRATIVE_DOCUMENT_DELETED = "ADMINISTRATIVE_DOCUMENT_DELETED";
  private final AdministrativeDocumentRepository repository;
  private final NotificationService notificationService;
  private final FileUploadService fileUploadService;
  private final MongoTemplate mongoTemplate;

  public AdministrativeDocumentService(AdministrativeDocumentRepository repository,
                                       NotificationService notificationService,
                                       FileUploadService fileUploadService,
                                       MongoTemplate mongoTemplate) {
    this.repository = repository;
    this.notificationService = notificationService;
    this.fileUploadService = fileUploadService;
    this.mongoTemplate = mongoTemplate;
  }

  @Async
  public void delete(String id) {
    log.warn("document {} will be really deleted", id);
    this.repository.deleteById(id);
    this.fileUploadService.deleteByCorrelationId(id);
    this.notificationService.sendEvent("document with id %s deleted".formatted(id), ADMINISTRATIVE_DOCUMENT_DELETED, id);
  }

  public List<AdministrativeDocument> findAll() {
    return repository.findAll();
  }

  public Optional<AdministrativeDocument> findById(String id) {
    return repository.findById(id);
  }

  public Page<AdministrativeDocument> search(AdministrativeDocumentSearchCriteria searchCriteria, Pageable pageable) {
    List<Criteria> criteriaList = new ArrayList<>();
    Criteria criteria = null;

    if (StringUtils.isNotEmpty(searchCriteria.getTitle())) {
      criteriaList.add(Criteria.where("title").regex(".*%s.*".formatted(searchCriteria.getTitle()), "i"));
    }

    if (StringUtils.isNotEmpty(searchCriteria.getDescription())) {
      criteriaList.add(Criteria.where("description").regex(".*%s.*".formatted(searchCriteria.getDescription()), "i"));
    }
    if (searchCriteria.getDateBefore() != null) {
      criteriaList.add(Criteria.where("dateCreation").lt(searchCriteria.getDateBefore()));
    }

    if (searchCriteria.getDateAfter() != null) {
      criteriaList.add(Criteria.where("dateCreation").gt(searchCriteria.getDateAfter()));
    }

    if (StringUtils.isNotEmpty(searchCriteria.getId())) {
      criteriaList.add(Criteria.where("id").is(searchCriteria.getId()));
    }

    if (searchCriteria.getTags() != null && !searchCriteria.getTags().isEmpty()) {
      criteriaList.add(Criteria.where("tags").in(searchCriteria.getTags()));
    }

    if (!criteriaList.isEmpty()) {
      criteria = new Criteria().andOperator(criteriaList.toArray(new Criteria[0]));
    }

    Query query = criteria != null ? Query.query(criteria) : new Query();


    long count = mongoTemplate.count(Query.of(query), AdministrativeDocument.class);

    List<AdministrativeDocument> ads = mongoTemplate.find(query.with(pageable), AdministrativeDocument.class);

    return PageableExecutionUtils.getPage(ads, pageable, () -> count);
  }

  @Async
  public void save(String id, String title, String description, List<String> tags, MultipartFile doc) {
    AdministrativeDocument ad = ofNullable(id).filter(StringUtils::isNotEmpty).flatMap(this.repository::findById)
                                              .orElseGet(() -> AdministrativeDocument.builder()
                                                                                     .id(IdGenerators.get())
                                                                                     .build());
    String documentUploadId = this.uploadAndDeleteIfExist(doc, ad.getId(), ad.getAttachmentId());
    log.info("saving document to mongo...");

    AdministrativeDocument save = repository.save(ad.toBuilder()
                                                    .title(title)
                                                    .description(description)
                                                    .updatedDate(new Date())
                                                    .attachmentId(documentUploadId)
                                                    .tags(ofNullable(tags).orElseGet(List::of))
                                                    .build());

    notificationService.sendEvent("document %s added or updated".formatted(save.getTitle()), ADMINISTRATIVE_DOCUMENT_ADDED, save.getId());

  }

  private String uploadAndDeleteIfExist(MultipartFile file, String id, String uploadId) {
    String newUploadId;
    Optional<String> optionalUploadId = ofNullable(file)
            .filter(Predicate.not(MultipartFile::isEmpty))
            .map(upload -> this.fileUploadService.upload(upload, id, false));

    if (optionalUploadId.isEmpty()) {
      newUploadId = ofNullable(uploadId)
              .orElseThrow(() -> new RuntimeException("You cannot save without a document!"));
    }
    else {
      newUploadId = optionalUploadId.get();
      ofNullable(uploadId).ifPresent(fileUploadService::delete);
    }
    return newUploadId;
  }
}
