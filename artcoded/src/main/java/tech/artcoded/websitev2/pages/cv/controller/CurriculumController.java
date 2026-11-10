package tech.artcoded.websitev2.pages.cv.controller;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tech.artcoded.websitev2.pages.cv.entity.Curriculum;
import tech.artcoded.websitev2.pages.cv.entity.DownloadCvRequest;
import tech.artcoded.websitev2.pages.cv.repository.DownloadCvRequestRepository;
import tech.artcoded.websitev2.pages.cv.service.CurriculumService;
import tech.artcoded.websitev2.template.OaTemplate;
import tech.artcoded.websitev2.template.OaTemplateService;
import tech.artcoded.websitev2.template.OaTemplateType;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/cv")
public class CurriculumController {

    private final CurriculumService curriculumService;
    private final DownloadCvRequestRepository downloadCvRequestRepository;
    private final OaTemplateService templateService;

    public CurriculumController(CurriculumService curriculumService,
            DownloadCvRequestRepository downloadCvRequestRepository, OaTemplateService templateService) {
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
    public ResponseEntity<List<DownloadCvRequest>> getDownloadCvRequests() {
        return ResponseEntity.ok(downloadCvRequestRepository.findAll());
    }

    @DeleteMapping("/download-requests")
    public ResponseEntity<Void> deleteDownloadCvRequests(@RequestParam("id") String id) {
        downloadCvRequestRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/download")
    public ResponseEntity<ByteArrayResource> download(@RequestBody DownloadCvRequest downloadCvRequest) {
        return this.curriculumService.download(downloadCvRequest);
    }

    @GetMapping("/admin-download")
    public ResponseEntity<ByteArrayResource> adminDownload() {
        return this.curriculumService.adminDownload();
    }

    @GetMapping("/list-templates")
    public List<OaTemplate> listTemplates() {
        return templateService.list(Optional.of(OaTemplateType.CV));
    }

    @DeleteMapping("/delete-template")
    public void deleteTemplate(@RequestParam("id") String id) {
        // kept for the current frontend: templates are now managed in /api/template
        this.templateService.findById(id).filter(t -> t.getType() == OaTemplateType.CV)
                .ifPresent(t -> this.templateService.delete(t.getId()));
    }

    @PostMapping(value = "/add-template", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public OaTemplate addTemplate(@RequestParam("name") String name, @RequestPart("template") MultipartFile template) {
        return this.templateService.save(null, name, null, OaTemplateType.CV, true, false, template);
    }
}
