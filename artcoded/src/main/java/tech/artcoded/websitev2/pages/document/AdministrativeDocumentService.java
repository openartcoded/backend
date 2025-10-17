package tech.artcoded.websitev2.pages.document;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.CachePut;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tech.artcoded.event.v1.document.AdministrativeDocumentAddedOrUpdated;
import tech.artcoded.event.v1.document.AdministrativeDocumentRemoved;
import tech.artcoded.websitev2.event.ExposedEventService;
import tech.artcoded.websitev2.notification.NotificationService;
import tech.artcoded.websitev2.upload.FileUploadService;
import tech.artcoded.websitev2.upload.ILinkable;
import tech.artcoded.websitev2.utils.helper.IdGenerators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static java.util.Optional.ofNullable;

@Service
@Slf4j
public class AdministrativeDocumentService implements ILinkable {
  private static final String ADMINISTRATIVE_DOCUMENT_ADDED = "ADMINISTRATIVE_DOCUMENT_ADDED";
  private static final String ADMINISTRATIVE_DOCUMENT_DELETED = "ADMINISTRATIVE_DOCUMENT_DELETED";
  private final AdministrativeDocumentRepository repository;
  private final NotificationService notificationService;
  private final FileUploadService fileUploadService;
  private final ExposedEventService exposedEventService;
  private final MongoTemplate mongoTemplate;

  public AdministrativeDocumentService(AdministrativeDocumentRepository repository,
      NotificationService notificationService,
      FileUploadService fileUploadService,
      ExposedEventService exposedEventService, MongoTemplate mongoTemplate) {
    this.repository = repository;
    this.notificationService = notificationService;
    this.fileUploadService = fileUploadService;
    this.exposedEventService = exposedEventService;
    this.mongoTemplate = mongoTemplate;
  }

  @Async
  public void delete(String id) {
    log.warn("document {} will be really deleted", id);

    repository.findById(id).filter(doc -> !doc.isLocked()).ifPresentOrElse(doc -> {
      repository.delete(doc);
      this.fileUploadService.deleteByCorrelationId(doc.getId());

      exposedEventService.sendEvent(AdministrativeDocumentRemoved.builder()
          .documentId(id)
          .build());
      this.notificationService.sendEvent("document with id %s deleted".formatted(doc.getId()),
          ADMINISTRATIVE_DOCUMENT_DELETED,
          doc.getId());

    }, () -> {
      this.notificationService.sendEvent(
          "document with id %s could not be deleted. Either it doesn't exist or is locked".formatted(id),
          ADMINISTRATIVE_DOCUMENT_DELETED,
          id);

    });
  }

  public List<AdministrativeDocument> findAll(Collection<String> documentIds) {
    var it = repository.findAllById(documentIds);
    List<AdministrativeDocument> results = new ArrayList<>();
    it.forEach(results::add);
    return results;
  }

  public List<AdministrativeDocument> findAll() {
    return repository.findAll();
  }

  public Optional<AdministrativeDocument> findById(String id) {
    return repository.findById(id);
  }

  public Optional<AdministrativeDocument> lockDocument(String id) {
    return toggleLockDocument(id, true);
  }

  public Optional<AdministrativeDocument> unlockDocument(String id) {
    return toggleLockDocument(id, false);
  }

  private Optional<AdministrativeDocument> toggleLockDocument(String id, boolean toggle) {
    return repository.findById(id).filter(doc -> toggle != doc.isLocked())
        .map(doc -> repository.save(doc.toBuilder().locked(toggle).updatedDate(new Date()).build()));
  }

  public Page<AdministrativeDocument> search(AdministrativeDocumentSearchCriteria searchCriteria, Pageable pageable) {
    List<Criteria> criteriaList = new ArrayList<>();
    Criteria criteria = null;

    if (searchCriteria.isBookmarked()) {
      criteriaList.add(Criteria.where("bookmarked").is(true));
    }
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

    exposedEventService.sendEvent(AdministrativeDocumentAddedOrUpdated.builder()
        .documentId(save.getId())
        .title(save.getTitle())
        .description(save.getDescription())
        .uploadId(save.getAttachmentId())
        .build());

    notificationService.sendEvent("document %s added or updated".formatted(save.getTitle()),
        ADMINISTRATIVE_DOCUMENT_ADDED, save.getId());

  }

  public Optional<AdministrativeDocument> toggleBookmarked(String id) {
    return repository.findById(id)
        .map(d -> repository.save(d.toBuilder().updatedDate(new Date()).bookmarked(!d.isBookmarked())
            .bookmarkedDate(d.isBookmarked() ? null : new Date())
            .build()));
  }

  private String uploadAndDeleteIfExist(MultipartFile file, String id, String uploadId) {
    String newUploadId;
    Optional<String> optionalUploadId = ofNullable(file)
        .filter(Predicate.not(MultipartFile::isEmpty))
        .map(upload -> this.fileUploadService.upload(upload, id, false));

    if (optionalUploadId.isEmpty()) {
      newUploadId = ofNullable(uploadId)
          .orElseThrow(() -> new RuntimeException("You cannot save without a document!"));
    } else {
      newUploadId = optionalUploadId.get();
      ofNullable(uploadId).ifPresent(fileUploadService::delete);
    }
    return newUploadId;
  }

  @Override
  @CachePut(cacheNames = "admin_doc_correlation_links", key = "#correlationId")
  public String getCorrelationLabel(String correlationId) {
    return this.findById(correlationId)
        .map(f -> "Document '%s' ".formatted(f.getTitle())).orElse(null);
  }
}
