package tech.artcoded.websitev2.pages.dossier;

import lombok.extern.slf4j.Slf4j;
import net.lingala.zip4j.ZipFile;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tech.artcoded.websitev2.domain.common.RateType;
import tech.artcoded.websitev2.mongodb.MongoManagementService;
import tech.artcoded.websitev2.notification.NotificationService;
import tech.artcoded.websitev2.pages.client.BillableClient;
import tech.artcoded.websitev2.pages.client.BillableClientService;
import tech.artcoded.websitev2.pages.client.ContractStatus;
import tech.artcoded.websitev2.pages.fee.Fee;
import tech.artcoded.websitev2.pages.fee.FeeService;
import tech.artcoded.websitev2.pages.invoice.BillTo;
import tech.artcoded.websitev2.pages.invoice.InvoiceGeneration;
import tech.artcoded.websitev2.pages.invoice.InvoiceService;
import tech.artcoded.websitev2.rest.util.MockMultipartFile;
import tech.artcoded.websitev2.upload.FileUploadService;
import tech.artcoded.websitev2.utils.helper.IdGenerators;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.text.ParseException;
import java.time.ZoneOffset;
import java.util.*;

import static java.net.URLConnection.guessContentTypeFromName;
import static java.time.LocalDate.parse;
import static java.time.format.DateTimeFormatter.ofPattern;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.io.FileUtils.readFileToByteArray;
import static org.apache.commons.io.IOUtils.copy;

@Service
@Slf4j
public class ImportOldDossierService {
  private static final String XLSX_DOSSIER_CREATED_SUCCESS_EVENT = "XLSX_DOSSIER_CREATED_SUCCESS_EVENT";
  private static final String XLSX_DOSSIER_CREATED_FAILURE_EVENT = "XLSX_DOSSIER_CREATED_FAILURE_EVENT";
  private static final String XLSX_FILE_NAME = "import.xlsx";
  private static final String CLIENT_SHEET = "Client";
  private static final String DOSSIER_SHEET = "Dossier";
  private static final String INVOICE_SHEET = "Invoice";
  private static final String EXPENSE_SHEET = "Expense";
  private static final DataFormatter DATA_FORMATTER = new DataFormatter();
  private final InvoiceService invoiceService;
  private final FeeService feeService;
  private final BillableClientService billableClientService;
  private final DossierService dossierService;
  private final NotificationService notificationService;
  private final FileUploadService fileService;
  private final MongoManagementService mongoManagementService;
  private final CloseActiveDossierService closeActiveDossierService;

  public ImportOldDossierService(InvoiceService invoiceService, FeeService feeService,
      BillableClientService billableClientService, DossierService dossierService,
      FileUploadService fileService,
      NotificationService notificationService, MongoManagementService mongoManagementService,
      CloseActiveDossierService closeActiveDossierService) {
    this.invoiceService = invoiceService;
    this.feeService = feeService;
    this.billableClientService = billableClientService;
    this.dossierService = dossierService;
    this.notificationService = notificationService;
    this.mongoManagementService = mongoManagementService;
    this.closeActiveDossierService = closeActiveDossierService;
    this.fileService = fileService;
  }

  @Async
  public void create(MultipartFile zip) {
    Date date = new Date();
    // extract zip file
    var tempDir = FileUtils.getTempDirectory();
    var tempDossierDir = new File(tempDir, IdGenerators.get());
    try {
      File extractedZip = new File(tempDir, requireNonNull(zip.getOriginalFilename()));
      try (var fos = new FileOutputStream(extractedZip); var zipIs = zip.getInputStream()) {
        copy(zipIs, fos);
      }

      try (var existingZip = new ZipFile(extractedZip)) {
        existingZip.extractAll(tempDossierDir.getAbsolutePath());
      }
      log.info("zip content: {}", FileUtils.listFiles(tempDossierDir, null, false));

      var xlsx = new File(tempDossierDir, XLSX_FILE_NAME);

      if (!xlsx.exists()) {
        throw new RuntimeException("xlsx file missing");
      }
      try (FileInputStream xlsxFIS = new FileInputStream(xlsx)) {
        Workbook workbook = new XSSFWorkbook(xlsxFIS);
        // extract xlsx metadata
        var clientRows = extractClients(workbook);
        var dossierRows = extractDossiers(workbook);
        var invoiceRows = extractInvoices(workbook, dossierRows, clientRows, tempDossierDir);
        var expenseRows = extractExpenses(workbook, dossierRows, tempDossierDir);

        log.info("expenseRows {}", expenseRows);
        log.info("invoiceRows {}", invoiceRows);
        log.info("dossierRows {}", dossierRows);

        // now that xlsx metadata are extracted, make a dump to make sure we can
        // rollback
        mongoManagementService.dump(true);

        // create clients
        for (var clientRow : clientRows) {
          BillableClient client = BillableClient.builder().address(clientRow.address)
              .contractStatus(ContractStatus.ONGOING).vatNumber(clientRow.vat)
              .phoneNumber(clientRow.phone).emailAddress(clientRow.email)
              .projectName(clientRow.projectName).rateType(clientRow.rateType)
              .startDate(clientRow.startDate).endDate(clientRow.endDate)
              .imported(true)
              .importedDate(date)
              .maxDaysToPay(clientRow.maxDaysToPay).city(clientRow.city)
              .rate(clientRow.rate).name(clientRow.name).build();
          if (billableClientService.findAll().stream()
              .noneMatch(
                  cli -> client.getName().equals(cli.getName()) || client.getVatNumber().equals(cli.getVatNumber()) ||
                      client.getEmailAddress().equals(cli.getEmailAddress()))) {
            billableClientService.save(client);
          }

        }
        Map<String, List<Fee>> expenseGroupedByDossier = new HashMap<>();

        for (var expenseRow : expenseRows) {
          List<MultipartFile> multipartFiles = new ArrayList<>();

          for (var file : expenseRow.files) {
            var mock = MockMultipartFile.builder().name(file.getName())
                .bytes(readFileToByteArray(file))
                .contentType(guessContentTypeFromName(file.getName()))
                .originalFilename(file.getName()).build();
            multipartFiles.add(mock);
          }

          var expense = feeService.save(expenseRow.title, expenseRow.description, expenseRow.receivedDate,
              multipartFiles);

          feeService.updatePrice(expense.getId(), expenseRow.hvat, expenseRow.vat);
          feeService.updateTag(expenseRow.label, List.of(expense.getId()));
          var expenseDossier = expenseGroupedByDossier.get(expenseRow.dossier.name);
          if (expenseDossier == null) {
            expenseDossier = new ArrayList<>();
          }
          expenseDossier.add(feeService.findById(expense.getId())
              .map(fee -> feeService.update(fee.toBuilder().imported(true).importedDate(date).build()))
              .orElseThrow(() -> new RuntimeException("exepense '%s' not found".formatted(expense.getId()))));

          expenseGroupedByDossier.put(expenseRow.dossier.name, expenseDossier);
        }

        Map<String, List<InvoiceGeneration>> invoiceGroupedByDossier = new HashMap<>();
        for (var invoiceRow : invoiceRows) {

          ClientRow client = invoiceRow.client;
          InvoiceGeneration invoiceToSave = InvoiceGeneration.builder().dateOfInvoice(invoiceRow.dateOfInvoice)
              .invoiceNumber(invoiceRow.number)
              .seqInvoiceNumber(-1L) // since it's an old invoice number, don't mess up with the sequence
              .invoiceTable(List.of(tech.artcoded.websitev2.pages.invoice.InvoiceRow.builder().amount(invoiceRow.amount)
                  .amountType(client.rateType)
                  .rateType(client.rateType).nature(invoiceRow.nature).period(invoiceRow.period)
                  .rate(invoiceRow.client.rate)
                  .projectName(client.projectName).build()))
              .dateCreation(invoiceRow.dateOfInvoice).taxRate(invoiceRow.taxRate).maxDaysToPay(client.maxDaysToPay)
              .uploadedManually(true).billTo(BillTo.builder().address(client.address).vatNumber(client.vat)
                  .city(client.city).emailAddress(client.email).clientName(client.name).build())
              .build();

          InvoiceGeneration invoiceGeneration = invoiceService.generateInvoice(invoiceToSave);

          invoiceService.manualUpload(
              MockMultipartFile.builder().name(invoiceRow.file.getName()).bytes(readFileToByteArray(invoiceRow.file))
                  .contentType(guessContentTypeFromName(invoiceRow.file.getName()))
                  .originalFilename(invoiceRow.file.getName()).build(),
              invoiceGeneration.getId(), invoiceRow.dateOfInvoice);

          var invoiceDossier = invoiceGroupedByDossier.get(invoiceRow.dossier.name);
          if (invoiceDossier == null) {
            invoiceDossier = new ArrayList<>();
          }
          invoiceDossier.add(invoiceService.findById(invoiceGeneration.getId())
              .map(invoice -> invoiceService.update(invoice.toBuilder().imported(true).importedDate(date).build()))
              .orElseThrow(() -> new RuntimeException("invoice %s not found")));
          invoiceGroupedByDossier.put(invoiceRow.dossier.name, invoiceDossier);
        }

        for (var dossierRow : dossierRows) {
          Dossier dossier = dossierService.save(Dossier.builder().description(dossierRow.description)
              .name(dossierRow.name).tvaDue(dossierRow.totalVat).build());

          var invoices = invoiceGroupedByDossier.get(dossierRow.name);
          log.debug("invoices {}", invoices);
          for (var invoice : invoices) {
            dossier = dossierService.processInvoice(invoice, dossier, dossierRow.date);
          }
          var expenses = expenseGroupedByDossier.get(dossierRow.name);
          log.debug("expenses {}", expenses);

          dossier = dossierService.processFees(expenses, dossier, dossierRow.date);

          dossier = dossierService.update(dossier.toBuilder().imported(true).importedDate(date).build());

          closeActiveDossierService.closeDossier(dossier, dossierRow.date);

        }

        log.debug("save imported zip");
        fileService.upload(zip, "", new Date(), false);

      }

      // finally, cleanup and send notification

      FileUtils.deleteDirectory(tempDossierDir);
      FileUtils.delete(extractedZip);
      notificationService.sendEvent("Dossier(s) created", XLSX_DOSSIER_CREATED_SUCCESS_EVENT, "");
    } catch (Exception e) {
      log.error("error while importing dossier(s)", e);
      notificationService.sendEvent("Failed to create dossier(s) from %s! Check the logs".formatted(zip.getName()),
          XLSX_DOSSIER_CREATED_FAILURE_EVENT, "");

    }

  }

  private List<ClientRow> extractClients(Workbook workbook) throws ParseException {
    Sheet sheet = workbook.getSheet(CLIENT_SHEET);
    Iterator<Row> iterator = sheet.iterator();
    iterator.next(); // skip first line
    List<ClientRow> clientRows = new ArrayList<>();
    while (iterator.hasNext()) {
      Row row = iterator.next();
      log.info("process extract client for row {}", row.getRowNum());
      String name = DATA_FORMATTER.formatCellValue(row.getCell(0)).trim();
      String email = DATA_FORMATTER.formatCellValue(row.getCell(1));
      String phone = DATA_FORMATTER.formatCellValue(row.getCell(2));
      String vat = DATA_FORMATTER.formatCellValue(row.getCell(3));
      String address = DATA_FORMATTER.formatCellValue(row.getCell(4));
      String city = DATA_FORMATTER.formatCellValue(row.getCell(5));
      String startDate = DATA_FORMATTER.formatCellValue(row.getCell(6));
      String endDate = DATA_FORMATTER.formatCellValue(row.getCell(7));
      String projectName = DATA_FORMATTER.formatCellValue(row.getCell(8));
      String maxDaysToPay = DATA_FORMATTER.formatCellValue(row.getCell(9));
      String rate = DATA_FORMATTER.formatCellValue(row.getCell(10));
      String rateType = DATA_FORMATTER.formatCellValue(row.getCell(11));

      clientRows.add(new ClientRow(name, projectName, email, phone, vat, address, city, parseDate(startDate),
          parseDate(endDate), Integer.valueOf(maxDaysToPay), new BigDecimal(rate), RateType.valueOf(rateType)));

    }
    return clientRows;

  }

  private Date parseDate(String s) {
    log.info("trying to parse {}", s);
    return new Date(parse(s, ofPattern("dd/MM/yyyy")).atTime(11, 0, 0)
        .toInstant(ZoneOffset.ofHoursMinutes(11, 0)).toEpochMilli());

  }

  private List<DossierRow> extractDossiers(Workbook workbook) throws ParseException {
    Sheet sheet = workbook.getSheet(DOSSIER_SHEET);
    Iterator<Row> iterator = sheet.iterator();
    iterator.next(); // skip first line
    List<DossierRow> dossierRows = new ArrayList<>();
    while (iterator.hasNext()) {
      Row row = iterator.next();
      log.info("process extract dossiers for row {}", row.getRowNum());
      String name = DATA_FORMATTER.formatCellValue(row.getCell(0)).trim();
      String date = DATA_FORMATTER.formatCellValue(row.getCell(1));
      if (StringUtils.isEmpty(date)) {
        log.error("dossier extraction error: date is empty!");
      }
      String description = DATA_FORMATTER.formatCellValue(row.getCell(2));
      String vat = DATA_FORMATTER.formatCellValue(row.getCell(3));

      dossierRows.add(new DossierRow(name, parseDate(date), description, new BigDecimal(vat)));

    }
    return dossierRows;

  }

  private List<InvoiceRow> extractInvoices(Workbook workbook, List<DossierRow> dossierRows, List<ClientRow> clientRows,
      File directory) throws ParseException {
    Sheet sheet = workbook.getSheet(INVOICE_SHEET);
    Iterator<Row> iterator = sheet.iterator();
    iterator.next(); // skip first line
    List<InvoiceRow> invoiceRows = new ArrayList<>();
    while (iterator.hasNext()) {
      Row row = iterator.next();
      log.info("process extract invoices for row {}", row.getRowNum());

      String number = DATA_FORMATTER.formatCellValue(row.getCell(0));
      String nature = DATA_FORMATTER.formatCellValue(row.getCell(1));
      String period = DATA_FORMATTER.formatCellValue(row.getCell(2));
      String dateOfInvoice = DATA_FORMATTER.formatCellValue(row.getCell(3));
      if (StringUtils.isEmpty(dateOfInvoice)) {
        log.error("invoice extraction error: date of invoice is empty!");
      }
      BigDecimal taxRate = new BigDecimal(DATA_FORMATTER.formatCellValue(row.getCell(4)));
      ClientRow clientRow = clientRows.stream()
          .filter(client -> client.name().equals(DATA_FORMATTER.formatCellValue(row.getCell(5)).trim())).findFirst()
          .orElseThrow(() -> new RuntimeException("Client not found!"));
      BigDecimal amount = new BigDecimal(DATA_FORMATTER.formatCellValue(row.getCell(6)));

      DossierRow dossierRow = dossierRows.stream()
          .filter(dossier -> dossier.name().equals(DATA_FORMATTER.formatCellValue(row.getCell(7)).trim())).findFirst()
          .orElseThrow(() -> new RuntimeException("Dossier not found!"));
      File file = new File(directory, DATA_FORMATTER.formatCellValue(row.getCell(8)).trim());
      if (!file.exists()) {
        throw new RuntimeException("file doesn't exist for invoice %s".formatted(number));
      }

      invoiceRows
          .add(new InvoiceRow(number, nature, period, parseDate(dateOfInvoice), taxRate, clientRow, amount, dossierRow,
              file));

    }
    return invoiceRows;

  }

  private List<ExpenseRow> extractExpenses(Workbook workbook, List<DossierRow> dossierRows, File directory)
      throws ParseException {
    Sheet sheet = workbook.getSheet(EXPENSE_SHEET);
    Iterator<Row> iterator = sheet.iterator();
    iterator.next(); // skip first line
    List<ExpenseRow> expenseRows = new ArrayList<>();
    while (iterator.hasNext()) {
      Row row = iterator.next();
      log.info("process extract expenses for row {}", row.getRowNum());
      String title = DATA_FORMATTER.formatCellValue(row.getCell(0));
      String description = DATA_FORMATTER.formatCellValue(row.getCell(1));
      String receivedDate = DATA_FORMATTER.formatCellValue(row.getCell(2));
      if (StringUtils.isEmpty(receivedDate)) {
        log.error("expense extraction error: receivedDate is empty!");
      }
      String label = DATA_FORMATTER.formatCellValue(row.getCell(3));

      DossierRow dossierRow = dossierRows.stream()
          .filter(dossier -> dossier.name().equals(DATA_FORMATTER.formatCellValue(row.getCell(4)).trim())).findFirst()
          .orElseThrow(() -> new RuntimeException("Dossier not found!"));

      String fileNames = DATA_FORMATTER.formatCellValue(row.getCell(5)).trim();
      var files = Arrays.stream(fileNames.split(","))
          .filter(StringUtils::isNotEmpty)
          .map(fileName -> new File(directory, fileName.trim())).peek(file -> {
            if (!file.exists()) {
              throw new RuntimeException("file doesn't exist for expense %s".formatted(title));
            }
          }).toList();

      BigDecimal hvat = new BigDecimal(DATA_FORMATTER.formatCellValue(row.getCell(6)));
      BigDecimal vat = new BigDecimal(DATA_FORMATTER.formatCellValue(row.getCell(7)));

      expenseRows.add(new ExpenseRow(title, description, parseDate(receivedDate), label, dossierRow, files, hvat, vat));

    }
    return expenseRows;

  }

  record ClientRow(String name, String projectName, String email, String phone,
      String vat, String address, String city,
      Date startDate, Date endDate, Integer maxDaysToPay,
      BigDecimal rate, RateType rateType) {
  }

  record DossierRow(String name, Date date, String description,
      BigDecimal totalVat) {
  }

  record InvoiceRow(String number, String nature, String period,
      Date dateOfInvoice, BigDecimal taxRate,
      ClientRow client, BigDecimal amount, DossierRow dossier,
      File file) {
  }

  record ExpenseRow(String title, String description, Date receivedDate,
      String label, DossierRow dossier, List<File> files,
      BigDecimal hvat, BigDecimal vat) {
  }

}
