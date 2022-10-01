package tech.artcoded.websitev2.pages.cv.service;

import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tech.artcoded.websitev2.pages.cv.entity.CurriculumFreemarkerTemplate;
import tech.artcoded.websitev2.pages.cv.repository.CurriculumTemplateRepository;
import tech.artcoded.websitev2.upload.FileUploadService;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static tech.artcoded.websitev2.utils.func.CheckedSupplier.toSupplier;

@Service
public class CurriculumTemplateService {
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
}
