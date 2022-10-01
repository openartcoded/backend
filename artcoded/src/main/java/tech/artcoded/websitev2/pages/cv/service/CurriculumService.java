package tech.artcoded.websitev2.pages.cv.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import tech.artcoded.websitev2.notification.NotificationService;
import tech.artcoded.websitev2.pages.cv.entity.Curriculum;
import tech.artcoded.websitev2.pages.cv.entity.DownloadCvRequest;
import tech.artcoded.websitev2.pages.cv.entity.Person;
import tech.artcoded.websitev2.pages.cv.entity.Skill;
import tech.artcoded.websitev2.pages.cv.repository.CurriculumRepository;
import tech.artcoded.websitev2.pages.cv.repository.DownloadCvRequestRepository;
import tech.artcoded.websitev2.rest.util.RestUtil;
import tech.artcoded.websitev2.utils.helper.IdGenerators;

import java.util.Comparator;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static java.util.Optional.ofNullable;

@Service
@Slf4j
public class CurriculumService {
  private static final String NOTIFICATION_TYPE = "CV_REQUEST";

  private final CurriculumRepository repository;
  private final DownloadCvRequestRepository downloadCvRequestRepository;
  private final NotificationService notificationService;
  private final CvToPrintService cvToPrintService;
  private final CurriculumRdfService curriculumRdfService;


  public CurriculumService(CurriculumRepository repository,
                           DownloadCvRequestRepository downloadCvRequestRepository,
                           NotificationService notificationService,
                           CvToPrintService cvToPrintService,
                           CurriculumRdfService curriculumRdfService) {
    this.repository = repository;
    this.downloadCvRequestRepository = downloadCvRequestRepository;
    this.notificationService = notificationService;
    this.cvToPrintService = cvToPrintService;
    this.curriculumRdfService = curriculumRdfService;
  }


  private Optional<Curriculum> getCv() {
    return repository.findAll().stream()
      .map(curriculum -> curriculum.toBuilder()
        .experiences(curriculum.getExperiences()
          .stream()
          .sorted()
          .toList())
        .scholarHistories(curriculum.getScholarHistories()
          .stream()
          .sorted()
          .toList())
        .skills(curriculum.getSkills().stream()
          .sorted(Comparator.comparingInt(Skill::getPriority)
            .reversed()).toList())
        .build())

      .findFirst();
  }

  @Cacheable(cacheNames = "curriculum",
    key = "'publicCv'")
  public Curriculum getPublicCurriculum() {
    return getCv()
      .map(curriculum -> curriculum.toBuilder()
        .person(Person.builder()
          .firstname(curriculum.getPerson().getFirstname())
          .lastname(curriculum.getPerson().getLastname())
          .title(curriculum.getPerson().getTitle())
          .githubUrl(curriculum.getPerson().getGithubUrl())
          .linkedinUrl(curriculum.getPerson().getLinkedinUrl())
          .build())
        .freemarkerTemplateId(null)
        .build())
      .orElseThrow(() -> new RuntimeException("cv not found!"));
  }

  public Curriculum getFullCurriculum() {
    return getCv().orElseThrow(() -> new RuntimeException("cv not found!"));
  }

  @CacheEvict(cacheNames = "curriculum",
    allEntries = true)
  public Curriculum update(Curriculum curriculum) {
    Curriculum updatedCv = getCv()
      .map(
        cv ->
          cv.toBuilder()
            .updatedDate(new Date())
            .introduction(curriculum.getIntroduction())
            .person(curriculum.getPerson())
            .freemarkerTemplateId(ofNullable(curriculum.getFreemarkerTemplateId()).orElse(cv.getFreemarkerTemplateId()))
            .experiences(curriculum.getExperiences())
            .hobbies(curriculum.getHobbies())
            .personalProjects(curriculum.getPersonalProjects())
            .scholarHistories(curriculum.getScholarHistories())
            .skills(curriculum.getSkills())
            .build())
      .map(this.repository::save)
      .orElseThrow(() -> new RuntimeException("cv not found!"));
    cvToPrintService.invalidateCache();
    CompletableFuture.runAsync(this::cacheCv);
    curriculumRdfService.pushTriples(updatedCv.getId());
    return updatedCv;
  }


  public ResponseEntity<ByteArrayResource> download(DownloadCvRequest downloadCvRequest) {
    CompletableFuture.runAsync(() -> {
      DownloadCvRequest dcr = this.downloadCvRequestRepository.save(
        downloadCvRequest.toBuilder().dateReceived(new Date()).id(IdGenerators.get()).build());

      this.notificationService.sendEvent("New CV Request (%s)".formatted(dcr.getEmail()), NOTIFICATION_TYPE, dcr.getId());
    });

    return this.adminDownload();
  }

  public ResponseEntity<ByteArrayResource> adminDownload() {
    return getCv().stream()
      .map(
        cv ->
          RestUtil.transformToByteArrayResource(
            "cv-" + System.currentTimeMillis() + ".pdf",
            "application/pdf",
            cvToPrintService.cvToPdf(cv).getData()))
      .findFirst()
      .orElseGet(ResponseEntity.notFound()::build);

  }


  @EventListener(ApplicationReadyEvent.class)
  public void init() {
    cacheCv();
  }

  void cacheCv() {
    log.info("cache cv...");
    this.getCv().ifPresent(cvToPrintService::cvToPdf);
    log.info("cv cached.");
  }

  @CacheEvict(cacheNames = "curriculum",
    allEntries = true)
  public void evictCache() {
    this.cvToPrintService.invalidateCache();
  }
}
