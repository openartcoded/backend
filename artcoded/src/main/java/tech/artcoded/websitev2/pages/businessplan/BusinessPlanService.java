package tech.artcoded.websitev2.pages.businessplan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.pages.businessplan.BusinessPlanFigures.ExpenseLine;
import tech.artcoded.websitev2.pages.businessplan.BusinessPlanFigures.InvoiceLine;
import tech.artcoded.websitev2.pages.client.BillableClient;
import tech.artcoded.websitev2.pages.client.BillableClientService;
import tech.artcoded.websitev2.pages.dossier.report.DossierReportService.ReportFile;
import tech.artcoded.websitev2.pages.dossier.report.ReportCache;
import tech.artcoded.websitev2.pages.fee.ExceptionalExpenses;
import tech.artcoded.websitev2.pages.fee.Fee;
import tech.artcoded.websitev2.pages.fee.FeeRepository;
import tech.artcoded.websitev2.pages.invoice.InvoiceGeneration;
import tech.artcoded.websitev2.pages.invoice.InvoiceGenerationRepository;
import tech.artcoded.websitev2.pages.personal.PersonalInfoService;
import tech.artcoded.websitev2.template.OaTemplateRenderer;
import tech.artcoded.websitev2.template.OaTemplateType;
import tech.artcoded.websitev2.upload.IFileUploadService;

/**
 * The business plan (pdf, default BUSINESS_PLAN template), generated on the fly from the invoices and the expenses.
 * Cached with the dossier reports ({@link ReportCache}): invalidated when an invoice, an expense, the organization or a
 * business plan template is saved, and every day (the projections depend on the current date).
 */
@Service
@Slf4j
public class BusinessPlanService {
    static final ZoneId ZONE = ZoneId.of("Europe/Brussels");
    private final InvoiceGenerationRepository invoiceRepository;
    private final FeeRepository feeRepository;
    private final PersonalInfoService personalInfoService;
    private final IFileUploadService fileUploadService;
    private final OaTemplateRenderer templateRenderer;
    private final ReportCache reportCache;
    private final BillableClientService clientService;

    public BusinessPlanService(InvoiceGenerationRepository invoiceRepository, FeeRepository feeRepository,
            PersonalInfoService personalInfoService, IFileUploadService fileUploadService,
            OaTemplateRenderer templateRenderer, ReportCache reportCache, BillableClientService clientService) {
        this.invoiceRepository = invoiceRepository;
        this.feeRepository = feeRepository;
        this.personalInfoService = personalInfoService;
        this.fileUploadService = fileUploadService;
        this.templateRenderer = templateRenderer;
        this.reportCache = reportCache;
        this.clientService = clientService;
    }

    static String cacheKey(BusinessPlanRequest request, LocalDate today) {
        // v2: without the student jobs
        return "business-plan-v2-%s-%s-%s".formatted(request.languageOrDefault().name().toLowerCase(),
                request.cacheKey(), today);
    }

    /** the pdf (cached) */
    public ReportFile generate(BusinessPlanRequest request, boolean refresh) {
        var today = LocalDate.now(ZONE);
        var key = cacheKey(request, today);
        if (refresh) {
            reportCache.evict(key);
        }
        return reportCache.get(key, () -> render(request, today));
    }

    ReportFile render(BusinessPlanRequest request, LocalDate today) {
        var org = personalInfoService.get();
        var report = report(request, today);
        var baseName = "%s-%s-%s".formatted(switch (request.languageOrDefault()) {
            case EN -> "business-plan";
            case FR -> "plan-financier";
            case NL -> "financieel-plan";
        }, StringUtils.defaultIfBlank(org.getOrganizationName(), "company").replaceAll("[^A-Za-z0-9]+", "-")
                .toLowerCase(), today);
        var pdf = templateRenderer.renderDefault(OaTemplateType.BUSINESS_PLAN, Map.of("plan", report), baseName);
        log.info("business plan {} generated ({} bytes)", baseName, pdf.length);
        return new ReportFile(baseName + ".pdf", pdf, null);
    }

    /** the model of the template */
    BusinessPlanReport report(BusinessPlanRequest request, LocalDate today) {
        var org = personalInfoService.get();
        return BusinessPlanView.report(figures(request, today), request, org, logo(org.getLogoUploadId()));
    }

    BusinessPlanFigures figures(BusinessPlanRequest request, LocalDate today) {
        var students = clientService.findAll().stream().filter(BillableClient::isStudent).toList();
        var invoices = invoiceRepository.findAll().stream().filter(i -> !i.isLogicalDelete())
                .filter(i -> !isStudentJob(i, students)).map(BusinessPlanService::invoiceLine).toList();
        var fees = feeRepository.findAll();
        var threshold = ExceptionalExpenses.threshold(ExceptionalExpenses.median(fees),
                ExceptionalExpenses.DEFAULT_FACTOR, ExceptionalExpenses.DEFAULT_MIN_AMOUNT);
        var expenses = fees.stream().filter(f -> f.getPriceHVAT() != null).map(f -> expenseLine(f, threshold)).toList();
        return BusinessPlanCalculator.compute(invoices, expenses, threshold, request, today);
    }

    /** billed to a client flagged as student: a job as an employee, not the activity of the company */
    static boolean isStudentJob(InvoiceGeneration invoice, List<BillableClient> students) {
        var billTo = invoice.getBillTo();
        if (billTo == null || students.isEmpty()) {
            return false;
        }
        var vat = clean(billTo.getVatNumber());
        var name = StringUtils.trimToEmpty(billTo.getClientName());
        return students.stream().anyMatch(s -> (!vat.isEmpty() && vat.equals(clean(s.getVatNumber())))
                || (!name.isEmpty() && name.equalsIgnoreCase(StringUtils.trimToEmpty(s.getName()))));
    }

    private static String clean(String vatNumber) {
        return StringUtils.defaultString(vatNumber).replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }

    static InvoiceLine invoiceLine(InvoiceGeneration invoice) {
        var date = toLocalDate(Optional.ofNullable(invoice.getDateOfInvoice()).orElse(invoice.getDateCreation()));
        return new InvoiceLine(date.getYear(), date.getMonthValue(), invoice.getSubTotal());
    }

    static ExpenseLine expenseLine(Fee fee, BigDecimal threshold) {
        var date = Optional.ofNullable(fee.getDate()).orElse(fee.getDateCreation());
        return new ExpenseLine(date == null ? null : toLocalDate(date), fee.getSubject(), fee.getTag(),
                fee.getPriceHVAT(), ExceptionalExpenses.isExceptional(fee, threshold), fee.getExceptional() != null);
    }

    private static LocalDate toLocalDate(Date date) {
        return date.toInstant().atZone(ZONE).toLocalDate();
    }

    private String logo(String logoUploadId) {
        if (StringUtils.isBlank(logoUploadId)) {
            return "";
        }
        try {
            return fileUploadService.findOneById(logoUploadId)
                    .map(file -> "data:%s;base64,%s".formatted(
                            Objects.requireNonNullElse(file.getContentType(), MediaType.IMAGE_PNG_VALUE),
                            Base64.getEncoder().encodeToString(fileUploadService.uploadToByteArray(file))))
                    .orElse("");
        } catch (Exception e) {
            log.warn("could not read the logo {}: the business plan has none", logoUploadId, e);
            return "";
        }
    }
}
