package tech.artcoded.websitev2.pages.cv.service;

import org.apache.commons.io.IOUtils;
import org.springframework.cache.annotation.CachePut;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tech.artcoded.websitev2.pages.cv.entity.CurriculumFreemarkerTemplate;
import tech.artcoded.websitev2.pages.cv.repository.CurriculumTemplateRepository;
import tech.artcoded.websitev2.upload.FileUploadService;
import tech.artcoded.websitev2.upload.ILinkable;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static tech.artcoded.websitev2.utils.func.CheckedSupplier.toSupplier;

@Service
public class CurriculumTemplateService implements ILinkable {
  private final CurriculumTemplateRepository templateRepository;
  private final FileUploadService fileUploadService;

  public CurriculumTemplateService(CurriculumTemplateRepository templateRepository,
      FileUploadService fileUploadService) {
    this.templateRepository = templateRepository;
    this.fileUploadService = fileUploadService;
  }

  public List<CurriculumFreemarkerTemplate> listTemplates() {
    return templateRepository.findAll();
  }

  public void deleteTemplate(String id) {
    this.templateRepository.findById(id).ifPresent(cvFreemarkerTemplate -> {
      this.fileUploadService.deleteByCorrelationId(cvFreemarkerTemplate.getId());
      this.templateRepository.deleteById(cvFreemarkerTemplate.getId());
    });
  }

  public CurriculumFreemarkerTemplate addTemplate(String name, MultipartFile template) {
    var templ = CurriculumFreemarkerTemplate.builder().name(name).build();
    String uploadId = fileUploadService.upload(template, templ.getId(), false);
    return templateRepository.save(templ.toBuilder().templateUploadId(uploadId).build());
  }

  public String getFreemarkerTemplate(String templateId) {
    return templateRepository.findById(templateId)
        .flatMap(t -> fileUploadService.findOneById(t.getTemplateUploadId()))
        .map(fileUploadService::uploadToInputStream)
        .map(is -> toSupplier(() -> IOUtils.toString(is, StandardCharsets.UTF_8)).get())
        .orElseThrow(() -> new RuntimeException("no template found"));
  }

  @Override
  @CachePut(cacheNames = "cv_template_correlation_links", key = "#correlationId")
  public Optional<String> getCorrelationLabel(String correlationId) {
    return this.templateRepository.findById(correlationId)
        .map(t -> "CV Template %s".formatted(t.getName()));
  }
}
