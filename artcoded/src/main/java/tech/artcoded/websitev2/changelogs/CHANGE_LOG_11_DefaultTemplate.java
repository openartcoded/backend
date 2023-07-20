package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import tech.artcoded.websitev2.pages.invoice.InvoiceFreemarkerTemplate;
import tech.artcoded.websitev2.pages.invoice.InvoiceTemplateRepository;
import tech.artcoded.websitev2.rest.util.MockMultipartFile;
import tech.artcoded.websitev2.upload.FileUploadService;

import java.io.IOException;

import static org.apache.commons.io.IOUtils.toByteArray;

@Slf4j
@ChangeUnit(id = "default-template",
  order = "11",
  author = "Nordine Bittich")
public class CHANGE_LOG_11_DefaultTemplate {

  @RollbackExecution
  public void rollbackExecution() {
  }

  @Execution
  public void execute(InvoiceTemplateRepository templateRepository, FileUploadService fileUploadService) throws IOException {
    if (templateRepository.count()==0) {
      var oldTemplate = new ClassPathResource("invoice/template-2021-v2.ftl");
      InvoiceFreemarkerTemplate ift = InvoiceFreemarkerTemplate.builder()
        .name("Template 2021").build();

      log.info("save default invoice template... ");
      MockMultipartFile templ = MockMultipartFile.builder()
        .bytes(toByteArray(oldTemplate.getInputStream()))
        .name(oldTemplate.getFilename())
        .contentType(MediaType.TEXT_HTML_VALUE)
        .originalFilename(oldTemplate.getFilename())
        .build();
      String uploadId = fileUploadService.upload(templ, ift.getId(), false);

      templateRepository.save(ift.toBuilder().templateUploadId(uploadId).build());


    }

  }

}
