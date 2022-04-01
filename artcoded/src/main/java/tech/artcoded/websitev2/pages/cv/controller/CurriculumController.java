package tech.artcoded.websitev2.pages.cv.controller;


import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tech.artcoded.websitev2.pages.cv.entity.Curriculum;
import tech.artcoded.websitev2.pages.cv.entity.CurriculumFreemarkerTemplate;
import tech.artcoded.websitev2.pages.cv.entity.DownloadCvRequest;
import tech.artcoded.websitev2.pages.cv.repository.DownloadCvRequestRepository;
import tech.artcoded.websitev2.pages.cv.service.CurriculumService;
import tech.artcoded.websitev2.pages.cv.service.CurriculumTemplateService;
import tech.artcoded.websitev2.rest.annotation.SwaggerHeaderAuthentication;

import javax.inject.Inject;
import java.util.List;

@RestController
@RequestMapping("/api/cv")
@Slf4j
public class CurriculumController {

  private final CurriculumService curriculumService;
  private final DownloadCvRequestRepository downloadCvRequestRepository;
  private final CurriculumTemplateService templateService;

  @Inject
  public CurriculumController(

          CurriculumService curriculumService,
          DownloadCvRequestRepository downloadCvRequestRepository,
          CurriculumTemplateService templateService) {
    this.curriculumService = curriculumService;
    this.downloadCvRequestRepository = downloadCvRequestRepository;
    this.templateService = templateService;
  }

  @GetMapping
  public ResponseEntity<Curriculum> get() {
    return ResponseEntity.ok(curriculumService.getPublicCurriculum());
  }

  @PostMapping("/full")
  public ResponseEntity<Curriculum> getFullCv() {
    return ResponseEntity.ok(curriculumService.getFullCurriculum());
  }

  @PostMapping("/update")
  public ResponseEntity<Curriculum> update(@RequestBody Curriculum curriculum) {
    return ResponseEntity.ok(this.curriculumService.update(curriculum));
  }

  @PostMapping("/download-requests")
  @SwaggerHeaderAuthentication
  public ResponseEntity<List<DownloadCvRequest>> getDownloadCvRequests() {
    return ResponseEntity.ok(downloadCvRequestRepository.findAll());
  }

  @DeleteMapping("/download-requests")
  @SwaggerHeaderAuthentication
  public ResponseEntity<Void> deleteDownloadCvRequests(@RequestParam("id") String id) {
    downloadCvRequestRepository.deleteById(id);
    return ResponseEntity.ok().build();
  }

  @PostMapping("/download")
  public ResponseEntity<ByteArrayResource> download(
          @RequestBody DownloadCvRequest downloadCvRequest) {
    return this.curriculumService.download(downloadCvRequest);
  }

  @GetMapping("/admin-download")
  public ResponseEntity<ByteArrayResource> adminDownload() {
    return this.curriculumService.adminDownload();
  }

  @GetMapping("/list-templates")
  @SwaggerHeaderAuthentication
  public List<CurriculumFreemarkerTemplate> listTemplates() {
    return templateService.listTemplates();
  }

  @DeleteMapping("/delete-template")
  @SwaggerHeaderAuthentication
  public void deleteTemplate(@RequestParam("id") String id) {
    this.templateService.deleteTemplate(id);
  }

  @PostMapping(value = "/add-template",
               consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @SwaggerHeaderAuthentication
  public CurriculumFreemarkerTemplate addTemplate(@RequestParam("name") String name,
                                                  @RequestPart("template") MultipartFile template) {
    return this.templateService.addTemplate(name, template);
  }
}