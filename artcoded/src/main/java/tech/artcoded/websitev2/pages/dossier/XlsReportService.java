package tech.artcoded.websitev2.pages.dossier;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tech.artcoded.websitev2.pages.fee.Fee;
import tech.artcoded.websitev2.pages.fee.FeeRepository;
import tech.artcoded.websitev2.pages.invoice.InvoiceGeneration;
import tech.artcoded.websitev2.pages.invoice.InvoiceService;
import tech.artcoded.websitev2.rest.util.MockMultipartFile;
import tech.artcoded.websitev2.upload.FileUpload;
import tech.artcoded.websitev2.upload.IFileUploadService;
import tech.artcoded.websitev2.utils.helper.IdGenerators;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

@Service
@Slf4j
public class XlsReportService {
    private final DossierRepository dossierRepository;
    private final InvoiceService invoiceService;
    private final FeeRepository feeRepository;
    private final IFileUploadService fileUploadService;

    public XlsReportService(DossierRepository dossierRepository, InvoiceService invoiceService,
            FeeRepository feeRepository, IFileUploadService fileUploadService) {
        this.dossierRepository = dossierRepository;
        this.invoiceService = invoiceService;
        this.feeRepository = feeRepository;
        this.fileUploadService = fileUploadService;
    }

    public Optional<MultipartFile> generate(String dossierId) {
        Dossier dossier = dossierRepository.findById(dossierId)
                .orElseThrow(() -> new RuntimeException("dossier not found"));

        List<InvoiceGeneration> invoices = dossier.getInvoiceIds().stream().map(invoiceService::findById)
                .flatMap(Optional::stream).collect(Collectors.toList());
        var expenses = dossier.getFeeIds().stream().map(feeRepository::findById).flatMap(Optional::stream)
                .filter(f -> Objects.nonNull(f.getTag())).collect(Collectors.groupingBy(Fee::getTag));
        return generate(dossier, invoices, expenses);

    }

    public Optional<MultipartFile> generate(Dossier dossier, List<InvoiceGeneration> invoices,
            Map<String, List<Fee>> expenses) {

        try (Workbook workbook = new XSSFWorkbook()) {
            generateInvoiceSheet(workbook, invoices);
            generateExpenseSheet(workbook, expenses);
            var outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            if (workbook.getNumberOfSheets() > 0) {
                var filename = "informative-summary-dossier-%s-%s.xlsx"
                        .formatted(FilenameUtils.normalize(dossier.getName()), IdGenerators.get());
                return Optional.of(MockMultipartFile.builder().name(filename)
                        .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml")
                        .originalFilename(filename).bytes(outputStream.toByteArray()).build());
            }

        } catch (Exception e) {
            log.error("An error occurred while generating the xls report", e);
        }
        return Optional.empty();
    }

    public void generateInvoiceSheet(Workbook workbook, List<InvoiceGeneration> invoices) {
        if (!invoices.isEmpty()) {
            var eurCostFormat = getNumberFormat();
            var invoiceSheet = workbook.createSheet("My Invoices");
            invoiceSheet.setDefaultColumnWidth(15);
            var subTotalTotal = new BigDecimal(0);
            var vatTotalTotal = new BigDecimal(0);
            var totalTotal = new BigDecimal(0);
            var counter = new AtomicInteger(1);
            var header = invoiceSheet.createRow(0);
            header.createCell(0).setCellValue("#");
            header.createCell(1).setCellValue("Client");
            header.createCell(2).setCellValue("Date of invoice");
            header.createCell(3).setCellValue("Due date");
            header.createCell(4).setCellValue("SubTotal");
            header.createCell(5).setCellValue("VAT");
            header.createCell(6).setCellValue("Total");
            for (InvoiceGeneration invoiceGeneration : invoices) {
                var row = invoiceSheet.createRow(counter.getAndAdd(1));
                BigDecimal subTotal = ofNullable(invoiceGeneration.getSubTotal()).orElse(BigDecimal.ZERO);
                BigDecimal taxes = ofNullable(invoiceGeneration.getTaxes()).orElse(BigDecimal.ZERO);
                BigDecimal total = ofNullable(invoiceGeneration.getTotal()).orElse(BigDecimal.ZERO);
                subTotalTotal = subTotalTotal.add(subTotal);
                vatTotalTotal = vatTotalTotal.add(taxes);
                totalTotal = totalTotal.add(total);
                row.createCell(0).setCellValue(
                        ofNullable(invoiceGeneration.getNewInvoiceNumber()).orElse(invoiceGeneration.getReference()));
                row.createCell(1).setCellValue(invoiceGeneration.getClientName());
                setCellDate(workbook, row.createCell(2), invoiceGeneration.getDateOfInvoice());
                setCellDate(workbook, row.createCell(3), invoiceGeneration.getDueDate());
                row.createCell(4).setCellValue(eurCostFormat.format(subTotal));
                row.createCell(5).setCellValue(eurCostFormat.format(taxes));
                row.createCell(6).setCellValue(eurCostFormat.format(total));
            }
            Row row = invoiceSheet.createRow(counter.getAndAdd(1));
            CellStyle cellStyle = workbook.createCellStyle();
            cellStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            var totalCell = row.createCell(0);
            row.createCell(1).setCellStyle(cellStyle);
            row.createCell(2).setCellStyle(cellStyle);
            row.createCell(3).setCellStyle(cellStyle);
            totalCell.setCellValue("Total");
            totalCell.setCellStyle(cellStyle);
            var subTotalTotalCell = row.createCell(4);
            subTotalTotalCell.setCellValue(eurCostFormat.format(subTotalTotal.doubleValue()));
            subTotalTotalCell.setCellStyle(cellStyle);
            var vatTotalTotalCell = row.createCell(5);
            vatTotalTotalCell.setCellValue(eurCostFormat.format(vatTotalTotal.doubleValue()));
            vatTotalTotalCell.setCellStyle(cellStyle);
            var totalTotalCell = row.createCell(6);
            totalTotalCell.setCellValue(eurCostFormat.format(totalTotal.doubleValue()));
            totalTotalCell.setCellStyle(cellStyle);
        }
    }

    public void generateExpenseSheet(Workbook workbook, Map<String, List<Fee>> expenses) {
        if (!expenses.isEmpty()) {
            var eurCostFormat = getNumberFormat();

            for (Map.Entry<String, List<Fee>> entry : expenses.entrySet()) {
                var tag = entry.getKey();
                List<Fee> fees = entry.getValue();
                if (!fees.isEmpty()) {
                    var feeSheet = workbook.createSheet(StringUtils.capitalize(tag.toLowerCase()));
                    feeSheet.setDefaultColumnWidth(15);
                    var priceHvatTotal = new BigDecimal(0);
                    var vatTotal = new BigDecimal(0);
                    var totalTotal = new BigDecimal(0);
                    var counter = new AtomicInteger(1);
                    var header = feeSheet.createRow(0);
                    header.createCell(0).setCellValue("Files");
                    header.createCell(1).setCellValue("Archived date");
                    header.createCell(2).setCellValue("Price HVAT");
                    header.createCell(3).setCellValue("VAT");
                    header.createCell(4).setCellValue("Total");

                    for (Fee fee : fees) {
                        var fileNames = fee.getAttachmentIds().stream().map(fileUploadService::findOneById)
                                .flatMap(Optional::stream).map(FileUpload::getOriginalFilename)
                                .collect(Collectors.joining(","));

                        var row = feeSheet.createRow(counter.getAndAdd(1));

                        var priceHvat = ofNullable(fee.getPriceHVAT()).orElse(BigDecimal.ZERO);
                        var vat = ofNullable(fee.getVat()).orElse(BigDecimal.ZERO);
                        var priceTot = ofNullable(fee.getPriceTot()).orElse(BigDecimal.ZERO);
                        priceHvatTotal = priceHvatTotal.add(priceHvat);
                        vatTotal = vatTotal.add(vat);
                        totalTotal = totalTotal.add(priceTot);

                        row.createCell(0).setCellValue(
                                abbreviate(StringUtils.isEmpty(fileNames) ? fee.getSubject() : fileNames));
                        setCellDate(workbook, row.createCell(1), fee.getArchivedDate());
                        row.createCell(2).setCellValue(eurCostFormat.format(priceHvat.doubleValue()));
                        row.createCell(3).setCellValue(eurCostFormat.format(vat.doubleValue()));
                        row.createCell(4).setCellValue(eurCostFormat.format(priceTot));
                    }

                    Row row = feeSheet.createRow(counter.getAndAdd(1));
                    CellStyle cellStyle = workbook.createCellStyle();
                    cellStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
                    cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                    var totalCell = row.createCell(0);
                    row.createCell(1).setCellStyle(cellStyle);
                    totalCell.setCellValue("Total");
                    totalCell.setCellStyle(cellStyle);
                    var priceHvatTotalCell = row.createCell(2);
                    priceHvatTotalCell.setCellValue(eurCostFormat.format(priceHvatTotal.doubleValue()));
                    priceHvatTotalCell.setCellStyle(cellStyle);
                    var vatTotalTotalCell = row.createCell(3);
                    vatTotalTotalCell.setCellValue(eurCostFormat.format(vatTotal.doubleValue()));
                    vatTotalTotalCell.setCellStyle(cellStyle);
                    var totalTotalCell = row.createCell(4);
                    totalTotalCell.setCellValue(eurCostFormat.format(totalTotal.doubleValue()));
                    totalTotalCell.setCellStyle(cellStyle);
                }
            }
        }
    }

    private void setCellDate(Workbook wb, Cell cell, Date date) {
        CellStyle cellStyle = wb.createCellStyle();
        CreationHelper createHelper = wb.getCreationHelper();
        cellStyle.setDataFormat(createHelper.createDataFormat().getFormat("dd/mm/yyyy"));
        cell.setCellValue(date);
        cell.setCellStyle(cellStyle);
    }

    private String abbreviate(String val) {
        return StringUtils.abbreviate(val, 1024);
    }

    private NumberFormat getNumberFormat() {
        NumberFormat eurCostFormat = NumberFormat.getCurrencyInstance(Locale.GERMANY);
        eurCostFormat.setMinimumFractionDigits(2);
        eurCostFormat.setMaximumFractionDigits(2);
        return eurCostFormat;
    }

}
