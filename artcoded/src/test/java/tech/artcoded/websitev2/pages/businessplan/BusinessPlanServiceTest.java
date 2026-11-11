package tech.artcoded.websitev2.pages.businessplan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.openapitools.client.api.TemplateRoutesApi;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import tech.artcoded.websitev2.domain.common.RateType;
import tech.artcoded.websitev2.pages.businessplan.BusinessPlanRequest.Language;
import tech.artcoded.websitev2.pages.client.BillableClient;
import tech.artcoded.websitev2.pages.client.BillableClientService;
import tech.artcoded.websitev2.pages.dossier.report.ReportCache;
import tech.artcoded.websitev2.pages.fee.ExceptionalStatus;
import tech.artcoded.websitev2.pages.fee.Fee;
import tech.artcoded.websitev2.pages.fee.FeeRepository;
import tech.artcoded.websitev2.pages.invoice.BillTo;
import tech.artcoded.websitev2.pages.invoice.InvoiceGeneration;
import tech.artcoded.websitev2.pages.invoice.InvoiceGenerationRepository;
import tech.artcoded.websitev2.pages.invoice.InvoiceRow;
import tech.artcoded.websitev2.pages.personal.PersonalInfo;
import tech.artcoded.websitev2.pages.personal.PersonalInfoService;
import tech.artcoded.websitev2.template.HtmlToPdfRenderer;
import tech.artcoded.websitev2.template.OaTemplateRenderer;
import tech.artcoded.websitev2.template.OaTemplateService;
import tech.artcoded.websitev2.upload.IFileUploadService;

/**
 * Renders the bundled BUSINESS_PLAN template (no default template: FreeMarker) with a real renderer, the pdf printer is
 * mocked.
 */
class BusinessPlanServiceTest {
    @TempDir
    Path cacheDir;

    private final InvoiceGenerationRepository invoices = mock(InvoiceGenerationRepository.class);
    private final FeeRepository fees = mock(FeeRepository.class);
    private final PersonalInfoService personal = mock(PersonalInfoService.class);
    private final OaTemplateService templateService = mock(OaTemplateService.class);
    private final HtmlToPdfRenderer printer = mock(HtmlToPdfRenderer.class);
    private final AtomicReference<String> html = new AtomicReference<>();
    private final int lastYear = LocalDate.now(BusinessPlanService.ZONE).getYear() - 1;
    private BusinessPlanService service;

    private static Date date(int year, int month) {
        return Date.from(LocalDate.of(year, month, 15).atStartOfDay(ZoneId.of("Europe/Brussels")).toInstant());
    }

    private static InvoiceGeneration invoice(int year, int month, String days, String rate, boolean deleted) {
        return InvoiceGeneration.builder().dateOfInvoice(date(year, month)).logicalDelete(deleted)
                .billTo(BillTo.builder().clientName("Client").build())
                .invoiceTable(List.of(InvoiceRow.builder().amount(new BigDecimal(days)).amountType(RateType.DAYS)
                        .rate(new BigDecimal(rate)).rateType(RateType.DAYS).build()))
                .build();
    }

    private static InvoiceGeneration studentJob(int year, int month) {
        return invoice(year, month, "20", "100", false).toBuilder()
                .billTo(BillTo.builder().clientName("other name").vatNumber("BE 0111.222.333").build()).build();
    }

    private static Fee fee(int year, String tag, String price, ExceptionalStatus status) {
        return Fee.builder().date(date(year, 3)).subject("<b>" + tag + "</b>").tag(tag).priceHVAT(new BigDecimal(price))
                .vat(BigDecimal.ZERO).exceptional(status).build();
    }

    @BeforeEach
    void setUp() throws Exception {
        when(invoices.findAll()).thenReturn(List.of(invoice(lastYear, 3, "100", "700", false),
                invoice(lastYear, 9, "100", "700", false), invoice(lastYear, 10, "10", "500", false),
                invoice(lastYear, 11, "100", "999", true), invoice(lastYear - 1, 6, "180", "650", false),
                // a student job: left out
                studentJob(lastYear, 12)));
        when(fees.findAll()).thenReturn(List.of(fee(lastYear, "OFFICE", "80", null),
                fee(lastYear, "OFFICE", "90", null), fee(lastYear, "TAX", "9000", null),
                fee(lastYear, "LAPTOP", "2500", ExceptionalStatus.DENIED), fee(lastYear - 1, "OFFICE", "100", null),
                Fee.builder().subject("no price yet").date(date(lastYear, 1)).build()));
        when(personal.get()).thenReturn(PersonalInfo.builder().organizationName("Artcoded & Co")
                .vatNumber("BE0123456789").organizationAddress("Rue X 1").organizationPostCode("1000")
                .organizationCity("Bruxelles").ceoFullName("Nordine").build());
        when(templateService.getDefault(any())).thenReturn(Optional.empty());
        when(printer.render(anyString())).thenAnswer(a -> {
            html.set(a.getArgument(0));
            return "pdf".getBytes(StandardCharsets.UTF_8);
        });
        var renderer = new OaTemplateRenderer(templateService, mock(TemplateRoutesApi.class), printer);
        var clients = mock(BillableClientService.class);
        when(clients.findAll()).thenReturn(List.of(BillableClient.builder().name("Client").vatNumber("BE0999").build(),
                BillableClient.builder().name("Student Shop").vatNumber("BE0.111.222.333").student(true).build()));
        service = new BusinessPlanService(invoices, fees, personal, mock(IFileUploadService.class), renderer,
                new ReportCache(new ConcurrentMapCacheManager(), cacheDir.toString(), Duration.ofDays(7)), clients);
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    void rendersTheBundledTemplateInEveryLanguage(Language language) {
        var plan = service.generate(new BusinessPlanRequest(language, "IT consulting\nJava & RDF",
                new BigDecimal("20000"), 24, new BigDecimal("5"), "a car"), false);
        assertThat(plan.content()).isEqualTo("pdf".getBytes(StandardCharsets.UTF_8));
        var page = html.get();
        assertThat(page).contains("Artcoded &amp; Co", "BE0123456789", "IT consulting", "Java &amp; RDF", "<svg",
                // html is escaped
                "&lt;b&gt;TAX&lt;/b&gt;").doesNotContain("<b>TAX</b>", "${");
        assertThat(page).contains(switch (language) {
            case EN -> "Business plan";
            case FR -> "Plan financier";
            case NL -> "Financieel plan";
        });
        assertThat(plan.fileName()).startsWith(switch (language) {
            case EN -> "business-plan-artcoded-co-";
            case FR -> "plan-financier-artcoded-co-";
            case NL -> "financieel-plan-artcoded-co-";
        }).endsWith(".pdf");
    }

    @Test
    void theModelOfTheTemplate() {
        var report = service.report(new BusinessPlanRequest(Language.EN, null, null, null, null, null),
                LocalDate.now(BusinessPlanService.ZONE));
        // the deleted invoice and the student job are not counted: 200 * 700 + 10 * 500
        var last = report.getYears().stream().filter(y -> y.getYear().equals(String.valueOf(lastYear))).findFirst()
                .orElseThrow();
        assertThat(last.getRevenue()).isEqualTo("€145,000");
        // 145000 vs 117000
        assertThat(last.getGrowth()).isEqualTo("+23.9%");
        // the tax is exceptional, the denied laptop is a normal expense
        assertThat(last.getExceptionalExpenses()).isEqualTo("€9,000");
        assertThat(last.getNormalExpenses()).isEqualTo("€2,670");
        assertThat(report.getExceptional()).singleElement().satisfies(e -> {
            assertThat(e.getCategory()).isEqualTo("TAX");
            assertThat(e.getStatus()).isEqualTo("detected");
        });
        assertThat(report.getKpis()).isNotEmpty();
        assertThat(report.isHasForecast()).isTrue();
        assertThat(report.getForecast()).extracting(BusinessPlanReport.Row::getLabel).contains("Revenue",
                "Result before tax");
        assertThat(report.getScenarios()).extracting(BusinessPlanReport.Row::getLabel).containsExactly("Prudent",
                "Realistic", "Optimistic");
        assertThat(report.getMonths()).hasSize(12);
        assertThat(report.getYearsChart().isEmpty()).isFalse();
        // nothing about clients in the plan
        assertThat(report.toString()).doesNotContain("Client");
        assertThat(report.getLoan()).isNull();
    }

    @Test
    void cachedUntilRefreshed() throws Exception {
        var request = new BusinessPlanRequest(Language.FR, null, null, null, null, null);
        service.generate(request, false);
        service.generate(request, false);
        verify(printer, times(1)).render(anyString());
        service.generate(request, true);
        verify(printer, times(2)).render(anyString());
        // another language, another plan
        service.generate(new BusinessPlanRequest(Language.NL, null, null, null, null, null), false);
        verify(printer, times(3)).render(anyString());
    }

    @Test
    void everyTextExistsInEveryLanguageWithTheSamePlaceholders() {
        BusinessPlanTexts.all().forEach((key, texts) -> {
            var placeholders = java.util.regex.Pattern.compile("\\{\\w+}").matcher(texts[0]).results()
                    .map(java.util.regex.MatchResult::group).sorted().toList();
            for (var text : texts) {
                assertThat(text).as(key).isNotBlank();
                assertThat(java.util.regex.Pattern.compile("\\{\\w+}").matcher(text).results()
                        .map(java.util.regex.MatchResult::group).sorted().toList()).as(key)
                                .containsExactlyInAnyOrderElementsOf(placeholders);
            }
        });
    }
}
