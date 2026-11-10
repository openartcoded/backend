package tech.artcoded.websitev2.pages.dossier.report;

import static java.net.URLConnection.guessContentTypeFromName;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.HtmlUtils;

import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.pages.dossier.Dossier;
import tech.artcoded.websitev2.pages.dossier.DossierRepository;
import tech.artcoded.websitev2.pages.fee.FeeRepository;
import tech.artcoded.websitev2.pages.invoice.InvoiceGenerationRepository;
import tech.artcoded.websitev2.pages.personal.PersonalInfoService;
import tech.artcoded.websitev2.rest.util.MockMultipartFile;
import tech.artcoded.websitev2.template.OaTemplateRenderer;
import tech.artcoded.websitev2.template.OaTemplateType;
import tech.artcoded.websitev2.upload.IFileUploadService;
import tech.artcoded.websitev2.utils.helper.DateHelper;
import tech.artcoded.websitev2.utils.service.MailService;

/**
 * The report of a dossier for a month (pdf, default DOSSIER_REPORT template): the month and the year so far compared
 * with the previous years, expenses per tag, clients, vat of the dossier and what is left to process.
 */
@Service
@Slf4j
public class DossierReportService {
    static final ZoneId ZONE = ZoneId.of("Europe/Brussels");

    private final DossierRepository dossierRepository;
    private final InvoiceGenerationRepository invoiceRepository;
    private final FeeRepository feeRepository;
    private final PersonalInfoService personalInfoService;
    private final IFileUploadService fileUploadService;
    private final OaTemplateRenderer templateRenderer;
    private final MailService mailService;

    @Value("${application.admin.email}")
    private String adminEmail;

    public DossierReportService(DossierRepository dossierRepository, InvoiceGenerationRepository invoiceRepository,
            FeeRepository feeRepository, PersonalInfoService personalInfoService, IFileUploadService fileUploadService,
            OaTemplateRenderer templateRenderer, MailService mailService) {
        this.dossierRepository = dossierRepository;
        this.invoiceRepository = invoiceRepository;
        this.feeRepository = feeRepository;
        this.personalInfoService = personalInfoService;
        this.fileUploadService = fileUploadService;
        this.templateRenderer = templateRenderer;
        this.mailService = mailService;
    }

    /** the rendered report */
    public record ReportFile(String fileName, byte[] content, DossierReport report) {
    }

    public Optional<Dossier> activeDossier() {
        return dossierRepository.findOneByClosedIsFalse();
    }

    /**
     * The month a report covers when none is given: the previous month for the open dossier (the report is sent at the
     * beginning of a month), the month of the last invoice for a closed one.
     */
    public YearMonth defaultMonth(Dossier dossier) {
        var today = LocalDate.now(ZONE);
        var previousMonth = YearMonth.from(today).minusMonths(1);
        if (!dossier.isClosed()) {
            return previousMonth;
        }
        var lastInvoice = invoiceRepository.findAllById(Optional.ofNullable(dossier.getInvoiceIds()).orElseGet(Set::of))
                .stream().filter(i -> !i.isLogicalDelete()).map(DossierReportCalculator::invoiceDate)
                .filter(Objects::nonNull).max(LocalDate::compareTo);
        return lastInvoice.or(() -> Optional.ofNullable(dossier.getClosedDate()).map(DateHelper::toLocalDate))
                .map(YearMonth::from).filter(m -> m.isBefore(YearMonth.from(today))).orElse(previousMonth);
    }

    /** the report model (what the template receives as {@code report}) */
    public DossierReport report(Dossier dossier, YearMonth month) {
        var dossiers = dossierRepository.findAll();
        var invoiceIds = new HashSet<String>();
        var feeIds = new HashSet<String>();
        for (var d : dossiers) {
            Optional.ofNullable(d.getInvoiceIds()).ifPresent(invoiceIds::addAll);
            Optional.ofNullable(d.getFeeIds()).ifPresent(feeIds::addAll);
        }
        var processedInvoices = invoiceRepository.findAllById(invoiceIds).stream().filter(i -> !i.isLogicalDelete())
                .toList();
        var processedExpenses = feeRepository.findAllById(feeIds);
        var pendingInvoices = invoiceRepository.findByLogicalDeleteIsAndArchivedIsOrderByDateOfInvoiceDesc(false, false)
                .stream().filter(i -> !invoiceIds.contains(i.getId())).toList();
        var pendingExpenses = feeRepository.findByArchived(false).stream().filter(f -> !feeIds.contains(f.getId()))
                .toList();

        var report = DossierReportCalculator.compute(new DossierReportCalculator.Input(dossier, month,
                processedInvoices, processedExpenses, pendingInvoices, pendingExpenses, LocalDate.now(ZONE)));
        var personalInfo = personalInfoService.get();
        return report.toBuilder().organizationName(StringUtils.defaultString(personalInfo.getOrganizationName()))
                .organizationVatNumber(StringUtils.defaultString(personalInfo.getVatNumber()))
                .logo(logo(personalInfo.getLogoUploadId())).build();
    }

    public ReportFile generate(String dossierId, Optional<YearMonth> month) {
        var dossier = dossierRepository.findById(dossierId)
                .orElseThrow(() -> new IllegalArgumentException("dossier %s not found".formatted(dossierId)));
        return generate(dossier, month.orElseGet(() -> defaultMonth(dossier)));
    }

    public ReportFile generate(Dossier dossier, YearMonth month) {
        var report = report(dossier, month);
        var fileName = "report-%s-%s".formatted(StringUtils.defaultIfBlank(dossier.getName(), dossier.getId()), month)
                .replaceAll("[^A-Za-z0-9._-]+", "-");
        var pdf = templateRenderer.renderDefault(OaTemplateType.DOSSIER_REPORT, Map.of("report", report), fileName);
        log.info("dossier report {} generated ({} bytes)", fileName, pdf.length);
        return new ReportFile(fileName + ".pdf", pdf, report);
    }

    /** mails the report to the administrator, the insights in the body and the pdf attached */
    public void send(ReportFile file) {
        var report = file.report();
        var insights = report.getInsights().stream()
                .map(i -> "<li style=\"color:%s\">%s</li>".formatted(switch (i.getTone()) {
                    case "good" -> "#15803d";
                    case "bad" -> "#b91c1c";
                    default -> "#334155";
                }, StringUtils.defaultString(HtmlUtils.htmlEscape(i.getText())))).reduce("", String::concat);
        var body = """
                <p>The report of %s for %s is attached.</p>
                <ul>%s</ul>
                """.formatted(HtmlUtils.htmlEscape(report.getDossier().getName()), report.getMonthLabel(), insights);
        MultipartFile attachment = MockMultipartFile.builder().name(file.fileName()).originalFilename(file.fileName())
                .contentType(MediaType.APPLICATION_PDF_VALUE).bytes(file.content()).build();
        List<MultipartFile> attachments = List.of(attachment);
        mailService.sendMail(List.of(adminEmail),
                "Dossier report %s – %s".formatted(report.getDossier().getName(), report.getMonthLabel()), body, false,
                attachments);
    }

    private String logo(String logoUploadId) {
        if (StringUtils.isBlank(logoUploadId)) {
            return "";
        }
        try {
            return fileUploadService.findOneById(logoUploadId)
                    .map(file -> "data:%s;base64,%s".formatted(
                            StringUtils.defaultIfBlank(guessContentTypeFromName(file.getOriginalFilename()),
                                    MediaType.IMAGE_PNG_VALUE),
                            Base64.getEncoder().encodeToString(fileUploadService.uploadToByteArray(file))))
                    .orElse("");
        } catch (Exception e) {
            log.warn("could not read the logo {}: the report has none", logoUploadId, e);
            return "";
        }
    }
}
