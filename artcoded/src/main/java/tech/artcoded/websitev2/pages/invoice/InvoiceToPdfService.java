package tech.artcoded.websitev2.pages.invoice;

import freemarker.template.Configuration;
import freemarker.template.Template;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Service;
import tech.artcoded.websitev2.pages.personal.PersonalInfo;
import tech.artcoded.websitev2.pages.personal.PersonalInfoService;
import tech.artcoded.websitev2.rest.util.PdfToolBox;
import tech.artcoded.websitev2.upload.FileUploadService;

import javax.inject.Inject;
import java.io.StringReader;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static java.util.Optional.ofNullable;
import static org.springframework.ui.freemarker.FreeMarkerTemplateUtils.processTemplateIntoString;
import static tech.artcoded.websitev2.api.func.CheckedSupplier.toSupplier;

@Service
@Slf4j
public class InvoiceToPdfService {
  private final PersonalInfoService personalInfoService;
  private final InvoiceTemplateRepository templateRepository;
  private final FileUploadService fileUploadService;

  @Inject
  public InvoiceToPdfService(PersonalInfoService personalInfoService,
                             InvoiceTemplateRepository templateRepository,
                             FileUploadService fileUploadService) {
    this.personalInfoService = personalInfoService;
    this.templateRepository = templateRepository;
    this.fileUploadService = fileUploadService;
  }

  @SneakyThrows
  public byte[] invoiceToPdf(InvoiceGeneration ig) {
    PersonalInfo personalInfo = personalInfoService.get();
    String logo = fileUploadService.findOneById(personalInfo.getLogoUploadId())
                                   .map(gridFSFile -> Map.of("mediaType", URLConnection.guessContentTypeFromName(gridFSFile.getFilename()), "arr", fileUploadService.uploadToByteArray(gridFSFile)))
                                   .map(map -> "data:%s;base64,%s".formatted(map.get("mediaType"), Base64.getEncoder()
                                                                                                         .encodeToString((byte[]) map.get("arr"))))
                                   .orElseThrow(() -> new RuntimeException("Could not extract logo from personal info!!!"));
    var data = Map.of("invoice", ig, "personalInfo", personalInfo, "logo", logo);
    String strTemplate = ofNullable(ig.getFreemarkerTemplateId()).flatMap(templateRepository::findById)
                                                                 .flatMap(t -> fileUploadService.findOneById(t.getTemplateUploadId()))
                                                                 .map(fileUploadService::uploadToInputStream)
                                                                 .map(is -> toSupplier(() -> IOUtils.toString(is, StandardCharsets.UTF_8)).get())
                                                                 .orElseThrow(() -> new RuntimeException("legacy template missing"));
    Template template = new Template("name", new StringReader(strTemplate),
                                     new Configuration(Configuration.VERSION_2_3_31));
    String html = toSupplier(() -> processTemplateIntoString(template, data)).get();
    log.debug(html);
    return PdfToolBox.generatePDFFromHTMLV2(html);
  }
}
