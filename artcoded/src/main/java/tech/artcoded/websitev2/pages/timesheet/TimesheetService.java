package tech.artcoded.websitev2.pages.timesheet;

import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import tech.artcoded.event.v1.timesheet.TimesheetDeleted;
import tech.artcoded.event.v1.timesheet.TimesheetReleased;
import tech.artcoded.event.v1.timesheet.TimesheetReopened;
import tech.artcoded.websitev2.domain.common.RateType;
import tech.artcoded.websitev2.event.ExposedEventService;
import tech.artcoded.websitev2.notification.NotificationService;
import tech.artcoded.websitev2.pages.client.BillableClientService;
import tech.artcoded.websitev2.pages.invoice.InvoiceService;
import tech.artcoded.websitev2.rest.util.MockMultipartFile;
import tech.artcoded.websitev2.upload.FileUploadService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
public class TimesheetService {
  private static final String CLOSED_TIMESHEET = "CLOSED_TIMESHEET";
  private static final String REOPENED_TIMESHEET = "REOPENED_TIMESHEET";

  private final TimesheetRepository repository;
  private final TimesheetToPdfService timesheetToPdfService;
  private final FileUploadService fileUploadService;
  private final NotificationService notificationService;
  private final BillableClientService billableClientService;
  private final InvoiceService invoiceService;
  private final ExposedEventService eventService;

  public TimesheetService(TimesheetRepository repository,
      TimesheetToPdfService timesheetToPdfService,
      InvoiceService invoiceService,
      ExposedEventService exposedEventService,
      FileUploadService fileUploadService,
      NotificationService notificationService, BillableClientService billableClientService) {
    this.repository = repository;
    this.eventService = exposedEventService;
    this.timesheetToPdfService = timesheetToPdfService;
    this.invoiceService = invoiceService;
    this.fileUploadService = fileUploadService;
    this.notificationService = notificationService;
    this.billableClientService = billableClientService;
  }

  public Page<Timesheet> findAll(Pageable pageable) {
    return this.repository.findAll(pageable);
  }

  public Map<Integer, List<Timesheet>> findAllGroupedByYear() {
    return this.repository.findAll().stream().collect(Collectors.groupingBy(Timesheet::getYear));
  }

  public Map<Integer, Map<String, List<Timesheet>>> findAllGroupedByYearAndClientName() {
    return this.findAllGroupedByYear().entrySet()
        .stream()
        .map(e -> Map.entry(e.getKey(),
            e.getValue().stream().collect(Collectors.groupingBy(Timesheet::getClientNameOrNA))))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  public Timesheet saveOrUpdateTimesheet(Timesheet timesheet) {
    Timesheet timesheetFromDb = this.repository.findById(timesheet.getId()).orElse(timesheet);
    if (timesheetFromDb.isClosed()) {
      throw new RuntimeException("cannot update a closed timesheet");
    }

    return repository.save(timesheetFromDb.toBuilder()
        .name(timesheet.getName())
        .periods(timesheet.getPeriods())
        .build());
  }

  public TimesheetPeriod saveOrUpdateTimesheetPeriod(String id, TimesheetPeriod timesheetPeriod) {
    Timesheet ts = this.repository.findById(id)
        .orElseThrow(() -> new RuntimeException("Timesheet %s not found".formatted(id)));
    if (ts.isClosed()) {
      throw new RuntimeException("cannot modify a closed timesheet");
    }

    TimesheetPeriod newPeriod = ts.getPeriods()
        .stream()
        .filter(t -> t.getId().equals(timesheetPeriod.getId()))
        .map(TimesheetPeriod::toBuilder)
        .findFirst().orElseGet(timesheetPeriod::toBuilder)
        .shortDescription(timesheetPeriod.getShortDescription())
        .afternoonEndTime(timesheetPeriod.getAfternoonEndTime())
        .projectName(timesheetPeriod.getProjectName())
        .periodType(timesheetPeriod.getPeriodType())
        .date(timesheetPeriod.getDate())
        .afternoonStartTime(timesheetPeriod.getAfternoonStartTime())
        .morningEndTime(timesheetPeriod.getMorningEndTime())
        .morningStartTime(timesheetPeriod.getMorningStartTime())
        .build();

    Timesheet updatedTimesheet = ts.toBuilder()
        .periods(Stream.concat(ts.getPeriods()
            .stream()
            .filter(p -> !p.getId()
                .equals(newPeriod.getId())),
            Stream.of(newPeriod))
            .sorted(Comparator.comparing(TimesheetPeriod::getDate))
            .collect(Collectors.toList()))
        .build();
    Timesheet save = repository.save(updatedTimesheet);
    log.debug("timesheet {} saved", save.getId());
    return newPeriod;
  }

  protected Timesheet defaultTimesheet(String clientId) {
    var client = this.billableClientService.findById(clientId)
        .orElseThrow(() -> new RuntimeException("client %s doesn't exist".formatted(clientId)));
    return Timesheet.builder()
        .name(DateTimeFormatter.ofPattern("MM/yyyy").format(LocalDate.now()))
        .yearMonth(YearMonth.now())
        .clientId(client.getId())
        .clientName(client.getName())
        .settings(TimesheetSettings.builder()
            .minHoursPerDay(BigDecimal.ZERO)
            .maxHoursPerDay(new BigDecimal("8.5"))
            .defaultProjectName(client.getProjectName()).build())
        .periods(new ArrayList<>())
        .build();
  }

  @Async
  public void generateInvoiceFromTimesheet(String id) {
    Timesheet timesheet = this.repository.findById(id).orElseThrow();
    if (!timesheet.isClosed() || StringUtils.isEmpty(timesheet.getUploadId())) {
      throw new RuntimeException("timesheet must be closed");
    }
    if (timesheet.getInvoiceId().isPresent()) {
      throw new RuntimeException("invoice already generated from timesheet");
    }
    var client = billableClientService.findById(timesheet.getClientId())
        .orElseThrow(() -> new RuntimeException("client not found %s".formatted(timesheet.getClientId())));

    var invoice = invoiceService.newInvoiceFromNothing();
    var billTo = invoice.getBillTo();
    billTo.setCity(client.getCity());
    billTo.setAddress(client.getAddress());
    billTo.setVatNumber(client.getVatNumber());
    billTo.setClientName(client.getName());
    billTo.setEmailAddress(client.getEmailAddress());

    var invoiceRow = invoice.getInvoiceTable().get(0);
    invoice.setTaxRate(client.getTaxRate());
    invoice.setMaxDaysToPay(client.getMaxDaysToPay());
    invoice.setTimesheetId(Optional.of(timesheet.getId()));
    invoiceRow.setNature(client.getNature());
    invoiceRow.setRate(client.getRate());
    invoiceRow.setRateType(client.getRateType());
    invoiceRow.setAmount(timesheet.getNumberOfWorkingHours());
    invoiceRow.setAmountType(RateType.HOURS);
    invoiceRow.setPeriod(timesheet.getYearMonth().format(DateTimeFormatter.ofPattern("yyyy-MM")));
    var invoiceSaved = invoiceService.generateInvoice(invoice);
    timesheet.setInvoiceId(Optional.of(invoiceSaved.getId()));
    this.repository.save(timesheet);
  }

  @Async
  public void closeTimesheet(String id) {
    Timesheet timesheet = this.repository.findById(id).orElseThrow();
    if (timesheet.isClosed() || StringUtils.isNotEmpty(timesheet.getUploadId())) {
      throw new RuntimeException("timesheet already closed");
    }
    // generate pdf
    byte[] bytes = timesheetToPdfService.timesheetToPdf(timesheet);
    String uploadId = fileUploadService.upload(MockMultipartFile.builder()
        .name("timesheet-" + id + ".pdf")
        .originalFilename("timesheet-" + id + ".pdf")
        .contentType(MediaType.APPLICATION_PDF_VALUE)
        .bytes(bytes)
        .build(), timesheet.getId(), false);
    var saved = repository.save(timesheet.toBuilder()
        .closed(true)
        .uploadId(uploadId)
        .build());
    this.eventService.sendEvent(TimesheetReleased.builder()
        .uploadId(uploadId)
        .timesheetId(saved.getId())
        .clientName(saved.getClientNameOrNA())
        .period(saved.getName())
        .build());
    this.notificationService.sendEvent(
        "New Timesheet Ready (%s)".formatted(saved.getName()),
        CLOSED_TIMESHEET, saved.getId());

  }

  @Async
  public void reopenTimesheet(String id) {
    Timesheet timesheet = this.repository.findById(id).orElseThrow();
    if (!timesheet.isClosed()) {
      throw new RuntimeException("timesheet not closed");
    }
    fileUploadService.deleteByCorrelationId(id);
    if (timesheet.getInvoiceId().isPresent()) {
      invoiceService.deleteByTimesheetIdAndArchivedIsFalse(timesheet.getId());
    }
    var saved = repository.save(timesheet.toBuilder()
        .closed(false)
        .invoiceId(Optional.empty())
        .uploadId(null)
        .build());
    this.eventService.sendEvent(TimesheetReopened.builder()
        .timesheetId(saved.getId())
        .clientName(saved.getClientNameOrNA())
        .period(saved.getName())
        .build());
    this.notificationService.sendEvent(
        "Timesheet Reopened (%s)".formatted(saved.getName()),
        REOPENED_TIMESHEET, saved.getId());

  }

  public Optional<Timesheet> findByName(String name) {
    return repository.findByName(name);
  }

  public Optional<Timesheet> findByNameAndClientId(String name, String clientId) {
    return repository.findByNameAndClientId(name, clientId);
  }

  public long count() {
    return repository.count();
  }

  public void deleteById(String id) {
    findById(id)
        .ifPresent(ts -> {
          if (!ts.isClosed()) {
            fileUploadService.deleteByCorrelationId(ts.getId());
            this.eventService.sendEvent(TimesheetDeleted.builder()
                .timesheetId(ts.getId())
                .clientName(ts.getClientNameOrNA())
                .period(ts.getName())
                .build());
            repository.deleteById(ts.getId());
          }
        });
  }

  public Optional<Timesheet> findById(String id) {
    return repository.findById(id);
  }

  public Timesheet updateSettings(TimesheetSettingsForm settings) {
    var timesheet = repository.findById(settings.getTimesheetId())
        .orElseThrow(() -> new RuntimeException("timesheet not found %s".formatted(settings.getTimesheetId())));
    var client = billableClientService.findById(settings.getClientId())
        .orElseThrow(() -> new RuntimeException("client not found %s".formatted(settings.getClientId())));
    return repository.save(timesheet.toBuilder()
        .settings(timesheet.getSettings().toBuilder()
            .defaultProjectName(client.getProjectName())
            .maxHoursPerDay(settings.getMaxHoursPerDay())
            .minHoursPerDay(settings.getMinHoursPerDay()).build())
        .clientName(client.getName())
        .clientId(client.getId())
        .build());

  }
}
