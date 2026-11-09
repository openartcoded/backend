package tech.artcoded.websitev2.pages.dossier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import tech.artcoded.websitev2.domain.common.RateType;
import tech.artcoded.websitev2.pages.fee.Fee;
import tech.artcoded.websitev2.pages.fee.FeeRepository;
import tech.artcoded.websitev2.pages.invoice.BillTo;
import tech.artcoded.websitev2.pages.invoice.InvoiceGeneration;
import tech.artcoded.websitev2.pages.invoice.InvoiceRow;
import tech.artcoded.websitev2.pages.invoice.InvoiceService;
import tech.artcoded.websitev2.upload.FileUpload;
import tech.artcoded.websitev2.upload.IFileUploadService;

/** Generates real xlsx files with POI and reads them back. */
class XlsReportServiceTest {
    private final DossierRepository dossiers = mock(DossierRepository.class);
    private final InvoiceService invoices = mock(InvoiceService.class);
    private final FeeRepository fees = mock(FeeRepository.class);
    private final IFileUploadService uploads = mock(IFileUploadService.class);
    private final XlsReportService service = new XlsReportService(dossiers, invoices, fees, uploads);

    private static InvoiceGeneration invoice(String number, String client, String hours, String rate) {
        return InvoiceGeneration.builder().invoiceNumber(number).billTo(BillTo.builder().clientName(client).build())
                .taxRate(new BigDecimal("21"))
                .invoiceTable(List.of(InvoiceRow.builder().amount(new BigDecimal(hours)).rate(new BigDecimal(rate))
                        .amountType(RateType.HOURS).rateType(RateType.HOURS).build()))
                .build();
    }

    private static Fee fee(String id, String subject, String priceHVAT, String vat, List<String> attachmentIds) {
        return Fee.builder().id(id).subject(subject).tag("TRAIN").priceHVAT(new BigDecimal(priceHVAT))
                .vat(new BigDecimal(vat)).attachmentIds(attachmentIds).build();
    }

    private static Workbook read(byte[] bytes) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(bytes));
    }

    private static String cell(Sheet sheet, int row, int col) {
        return sheet.getRow(row).getCell(col).getStringCellValue();
    }

    private static List<String> sheetNames(Workbook workbook) {
        return IntStream.range(0, workbook.getNumberOfSheets()).mapToObj(workbook::getSheetName).toList();
    }

    @Test
    void invoicesAndExpensesAreExported() throws Exception {
        when(uploads.findOneById("u1"))
                .thenReturn(Optional.of(FileUpload.builder().originalFilename("ticket.pdf").build()));
        var expenses = Map.of("TRAIN", List.of(fee("f1", "train", "10", "0.60", List.of("u1"))));
        var dossier = Dossier.builder().name("Q1-2026").build();

        var report = service.generate(dossier, List.of(invoice("INV-1", "ACME", "8", "100")), expenses).orElseThrow();

        assertThat(report.getOriginalFilename()).startsWith("informative-summary-dossier-Q1-2026-").endsWith(".xlsx");
        try (var workbook = read(report.getBytes())) {
            assertThat(sheetNames(workbook)).containsExactly("My Invoices", "Train");

            var invoiceSheet = workbook.getSheet("My Invoices");
            assertThat(cell(invoiceSheet, 0, 1)).isEqualTo("Client");
            assertThat(cell(invoiceSheet, 1, 0)).isEqualTo("INV-1");
            assertThat(cell(invoiceSheet, 1, 1)).isEqualTo("ACME");
            // de-DE currency format: 800,00 €, 168,00 €, 968,00 €
            assertThat(cell(invoiceSheet, 1, 4)).contains("800,00");
            assertThat(cell(invoiceSheet, 1, 5)).contains("168,00");
            assertThat(cell(invoiceSheet, 1, 6)).contains("968,00");
            assertThat(cell(invoiceSheet, 2, 0)).isEqualTo("Total");
            assertThat(cell(invoiceSheet, 2, 6)).contains("968,00");

            var trainSheet = workbook.getSheet("Train");
            assertThat(cell(trainSheet, 1, 0)).isEqualTo("ticket.pdf");
            assertThat(cell(trainSheet, 1, 4)).contains("10,60");
            assertThat(cell(trainSheet, 2, 0)).isEqualTo("Total");
        }
    }

    @Test
    void expenseWithoutAttachmentsUsesItsSubject() throws Exception {
        var expenses = Map.of("TRAIN", List.of(fee("f1", "Brussels-Liège", "20", "1.20", null)));

        var report = service.generate(Dossier.builder().name("d").build(), List.of(), expenses).orElseThrow();

        try (var workbook = read(report.getBytes())) {
            assertThat(sheetNames(workbook)).containsExactly("Train");
            assertThat(cell(workbook.getSheet("Train"), 1, 0)).isEqualTo("Brussels-Liège");
        }
    }

    @Test
    void tagsThatAreNotValidSheetNamesStillProduceAReport() throws Exception {
        var expenses = new LinkedHashMap<String, List<Fee>>();
        expenses.put("CAR/FUEL", List.of(fee("f1", "fuel", "50", "10.50", List.of())));
        expenses.put("A VERY LONG TAG NAME THAT EXCEEDS THE EXCEL LIMIT", List.of(fee("f2", "x", "1", "0", List.of())));
        expenses.put("A VERY LONG TAG NAME THAT EXCEEDS THE EXCEL LIMIT TOO",
                List.of(fee("f3", "y", "2", "0", List.of())));

        var report = service.generate(Dossier.builder().name("d").build(), List.of(), expenses);

        assertThat(report).isPresent();
        try (var workbook = read(report.get().getBytes())) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(3);
            assertThat(sheetNames(workbook)).allSatisfy(name -> assertThat(name.length()).isLessThanOrEqualTo(31))
                    .doesNotHaveDuplicates().contains("Car fuel");
        }
    }

    @Test
    void sheetNamesAreUniqueIgnoringCase() throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            workbook.createSheet(XlsReportService.sheetName(workbook, "TRAIN"));
            workbook.createSheet(XlsReportService.sheetName(workbook, "train"));
            workbook.createSheet(XlsReportService.sheetName(workbook, "Train"));

            assertThat(sheetNames(workbook)).containsExactly("Train", "Train (2)", "Train (3)");
        }
    }

    @Test
    void emptyTagsAreSkipped() {
        var expenses = new LinkedHashMap<String, List<Fee>>();
        expenses.put("EMPTY", new ArrayList<>());

        // no sheet at all: nothing to report
        assertThat(service.generate(Dossier.builder().name("d").build(), List.of(), expenses)).isEmpty();
    }

    @Test
    void generateFromTheStoredDossier() throws Exception {
        var dossier = Dossier.builder().id("d1").name("Q3").invoiceIds(Set.of("i1", "missing"))
                .feeIds(Set.of("f1", "untagged")).build();
        when(dossiers.findById("d1")).thenReturn(Optional.of(dossier));
        when(invoices.findById("i1")).thenReturn(Optional.of(invoice("INV-9", "ACME", "1", "50")));
        when(invoices.findById("missing")).thenReturn(Optional.empty());
        when(fees.findById("f1")).thenReturn(Optional.of(fee("f1", "train", "10", "0", List.of())));
        when(fees.findById("untagged")).thenReturn(Optional.of(Fee.builder().id("untagged").build()));
        when(uploads.findOneById(anyString())).thenReturn(Optional.empty());

        var report = service.generate("d1").orElseThrow();

        try (var workbook = read(report.getBytes())) {
            assertThat(sheetNames(workbook)).containsExactly("My Invoices", "Train");
            assertThat(workbook.getSheet("My Invoices").getLastRowNum()).isEqualTo(2); // header, invoice, total
        }
    }
}
