package tech.artcoded.websitev2.pages.timesheet;

import freemarker.template.Configuration;
import freemarker.template.Template;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import tech.artcoded.websitev2.pages.personal.PersonalInfo;
import tech.artcoded.websitev2.pages.personal.PersonalInfoService;
import tech.artcoded.websitev2.rest.util.PdfToolBox;
import tech.artcoded.websitev2.upload.FileUploadService;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;

import static java.net.URLConnection.guessContentTypeFromName;
import static org.springframework.ui.freemarker.FreeMarkerTemplateUtils.processTemplateIntoString;
import static tech.artcoded.websitev2.utils.func.CheckedSupplier.toSupplier;

@Service
@Slf4j
public class TimesheetToPdfService {
    private final PersonalInfoService personalInfoService;
    private final FileUploadService fileUploadService;

    @Value("classpath:timesheet/timesheet-template-2021-v1.ftl")
    private Resource legacyTimesheetTemplate; // legacy because at some point it must be dynamic

    public TimesheetToPdfService(PersonalInfoService personalInfoService, FileUploadService fileUploadService) {
        this.personalInfoService = personalInfoService;
        this.fileUploadService = fileUploadService;
    }

    @SneakyThrows
    public byte[] timesheetToPdf(Timesheet timesheet) {
        PersonalInfo personalInfo = personalInfoService.get();
        String signature = fileUploadService.findOneById(personalInfo.getSignatureUploadId())
                .map(gridFSFile -> Map.of("mediaType", guessContentTypeFromName(gridFSFile.getOriginalFilename()),
                        "arr", fileUploadService.uploadToByteArray(gridFSFile)))
                .map(map -> "data:%s;base64,%s".formatted(map.get("mediaType"),
                        Base64.getEncoder().encodeToString((byte[]) map.get("arr"))))
                .orElseThrow(() -> new RuntimeException("Could not extract signature from personal info!!!"));
        Map<String, Object> data = Map.of("timesheet", timesheet.toBuilder()
                .periods(timesheet.getPeriods().stream().filter(p -> PeriodType.WORKING_DAY.equals(p.getPeriodType()))
                        .filter(TimesheetPeriod::isRowFilled).collect(Collectors.toList()))
                .build(), "personalInfo", personalInfo, "signature", signature);
        String strTemplate = toSupplier(
                () -> IOUtils.toString(legacyTimesheetTemplate.getInputStream(), StandardCharsets.UTF_8)).get();
        Template template = new Template("name", new StringReader(strTemplate),
                new Configuration(Configuration.VERSION_2_3_31));
        String html = toSupplier(() -> processTemplateIntoString(template, data)).get();
        log.debug(html);
        return PdfToolBox.generatePDFFromHTML(html);
    }
}
