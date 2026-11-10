package tech.artcoded.websitev2.pages.cv.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tech.artcoded.websitev2.pages.cv.entity.CurriculumFreemarkerTemplate;
import tech.artcoded.websitev2.pages.cv.repository.CurriculumTemplateRepository;
import tech.artcoded.websitev2.upload.IFileUploadService;

/**
 * @deprecated cv templates are now {@link tech.artcoded.websitev2.template.OaTemplate}s. Only kept for the legacy
 *             change unit that creates the first cv template on a fresh database (migrated to oaTemplates right
 *             after).
 */
@Deprecated
@Service
public class CurriculumTemplateService {
    private final CurriculumTemplateRepository templateRepository;
    private final IFileUploadService fileUploadService;

    public CurriculumTemplateService(CurriculumTemplateRepository templateRepository,
            IFileUploadService fileUploadService) {
        this.templateRepository = templateRepository;
        this.fileUploadService = fileUploadService;
    }

    public CurriculumFreemarkerTemplate addTemplate(String name, MultipartFile template) {
        var templ = CurriculumFreemarkerTemplate.builder().name(name).build();
        String uploadId = fileUploadService.upload(template, templ.getId(), false);
        return templateRepository.save(templ.toBuilder().templateUploadId(uploadId).build());
    }
}
