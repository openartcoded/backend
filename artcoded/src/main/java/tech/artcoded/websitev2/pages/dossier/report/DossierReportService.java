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
 * The report of a dossier (pdf, default DOSSIER_REPORT template) for one month (the monthly report) or for every month
 * of the dossier: the period compared with the same period of the previous years, month by month, expenses per tag,
 * clients, vat of the dossier and what is left to process.
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

    public Optional<Dossier> findDossier(String dossierId) {
        return Optional.ofNullable(dossierId).flatMap(dossierRepository::findById);
    }

    /** the dossier closed last (by closing date) */
    public Optional<Dossier> lastClosedDossier() {
        return dossierRepository.findByClosedOrderByUpdatedDateDesc(true).stream()
                .filter(d -> d.getClosedDate() != null).max(java.util.Comparator.comparing(Dossier::getClosedDate));
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

    /**
     * Every month of a dossier: from the month of its first invoice to the month of its last one (its expenses when it
     * has no invoice; its creation until its closing, or now, when it has neither).
     */
    public DossierReportCalculator.Period dossierPeriod(Dossier dossier) {
        var invoiceDates = invoiceRepository
                .findAllById(Optional.ofNullable(dossier.getInvoiceIds()).orElseGet(Set::of)).stream()
                .filter(i -> !i.isLogicalDelete()).map(DossierReportCalculator::invoiceDate).filter(Objects::nonNull)
                .toList();
        var dates = !invoiceDates.isEmpty() ? invoiceDates
                : feeRepository.findAllById(Optional.ofNullable(dossier.getFeeIds()).orElseGet(Set::of)).stream()
                        .map(DossierReportCalculator::expenseDate).filter(Objects::nonNull).toList();
        if (!dates.isEmpty()) {
            return new DossierReportCalculator.Period(
                    YearMonth.from(dates.stream().min(LocalDate::compareTo).orElseThrow()),
                    YearMonth.from(dates.stream().max(LocalDate::compareTo).orElseThrow()));
        }
        var to = YearMonth.from(Optional.ofNullable(dossier.getClosedDate()).map(DateHelper::toLocalDate)
                .orElseGet(() -> LocalDate.now(ZONE)));
        var from = Optional.ofNullable(dossier.getCreationDate()).map(DateHelper::toLocalDate).map(YearMonth::from)
                .filter(f -> !f.isAfter(to)).orElse(to);
        return new DossierReportCalculator.Period(from, to);
    }

    /** the report model of one month (what the template receives as {@code report}) */
    public DossierReport report(Dossier dossier, YearMonth month) {
        return report(dossier, month, month);
    }

    /** the report model of a period of months (what the template receives as {@code report}) */
    public DossierReport report(Dossier dossier, YearMonth from, YearMonth to) {
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

        var report = DossierReportCalculator.compute(new DossierReportCalculator.Input(dossier, from, to,
                processedInvoices, processedExpenses, pendingInvoices, pendingExpenses, LocalDate.now(ZONE)));
        var personalInfo = personalInfoService.get();
        return report.toBuilder().organizationName(StringUtils.defaultString(personalInfo.getOrganizationName()))
                .organizationVatNumber(StringUtils.defaultString(personalInfo.getVatNumber()))
                .logo(logo(personalInfo.getLogoUploadId())).build();
    }

    /**
     * @param month
     *            one month, empty: every month of the dossier
     */
    public ReportFile generate(String dossierId, Optional<YearMonth> month) {
        var dossier = dossierRepository.findById(dossierId)
                .orElseThrow(() -> new IllegalArgumentException("dossier %s not found".formatted(dossierId)));
        return month.map(m -> generate(dossier, m)).orElseGet(() -> generateWholeDossier(dossier));
    }

    /** the report of every month of the dossier */
    public ReportFile generateWholeDossier(Dossier dossier) {
        var period = dossierPeriod(dossier);
        return generate(dossier, period.from(), period.to());
    }

    /** the monthly report */
    public ReportFile generate(Dossier dossier, YearMonth month) {
        return generate(dossier, month, month);
    }

    public ReportFile generate(Dossier dossier, YearMonth from, YearMonth to) {
        var report = report(dossier, from, to);
        var fileName = "report-%s-%s".formatted(StringUtils.defaultIfBlank(dossier.getName(), dossier.getId()),
                from.equals(to) ? from : "%s_%s".formatted(from, to)).replaceAll("[^A-Za-z0-9._-]+", "-");
        var pdf = templateRenderer.renderDefault(OaTemplateType.DOSSIER_REPORT, Map.of("report", report), fileName);
        log.info("dossier report {} generated ({} bytes)", fileName, pdf.length);
        return new ReportFile(fileName + ".pdf", pdf, report);
    }

    /** mails the report to the administrator, the insights in the body and the pdf attached */
    public void send(ReportFile file) {
        var report = file.report();
        send(file, "Dossier report %s – %s".formatted(report.getDossier().getName(), report.getPeriodLabel()));
    }

    public void send(ReportFile file, String subject) {
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
                """.formatted(HtmlUtils.htmlEscape(report.getDossier().getName()),
                HtmlUtils.htmlEscape(report.getPeriodLabel()), insights);
        MultipartFile attachment = MockMultipartFile.builder().name(file.fileName()).originalFilename(file.fileName())
                .contentType(MediaType.APPLICATION_PDF_VALUE).bytes(file.content()).build();
        List<MultipartFile> attachments = List.of(attachment);
        mailService.sendMail(List.of(adminEmail), subject, body, false, attachments);
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
