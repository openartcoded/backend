package tech.artcoded.websitev2.pages.timesheet;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tech.artcoded.websitev2.pages.personal.PersonalInfo;
import tech.artcoded.websitev2.pages.personal.PersonalInfoService;
import tech.artcoded.websitev2.template.OaTemplateRenderer;
import tech.artcoded.websitev2.template.OaTemplateType;
import tech.artcoded.websitev2.upload.IFileUploadService;

import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;

import static java.net.URLConnection.guessContentTypeFromName;

@Service
@Slf4j
public class TimesheetToPdfService {
    private final PersonalInfoService personalInfoService;
    private final IFileUploadService fileUploadService;
    private final OaTemplateRenderer templateRenderer;

    public TimesheetToPdfService(PersonalInfoService personalInfoService, IFileUploadService fileUploadService,
            OaTemplateRenderer templateRenderer) {
        this.personalInfoService = personalInfoService;
        this.fileUploadService = fileUploadService;
        this.templateRenderer = templateRenderer;
    }

    /** Rendered with the default timesheet template. */
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
        return templateRenderer.renderDefault(OaTemplateType.TIMESHEET, data,
                "timesheet-%s".formatted(timesheet.getId()));
    }
}
