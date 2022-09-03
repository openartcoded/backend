package tech.artcoded.websitev2.pages.timesheet;

import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tech.artcoded.websitev2.notification.NotificationService;
import tech.artcoded.websitev2.pages.client.BillableClientRepository;
import tech.artcoded.websitev2.rest.util.MockMultipartFile;
import tech.artcoded.websitev2.upload.FileUploadService;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class TimesheetService {
  private static final String CLOSED_TIMESHEET = "CLOSED_TIMESHEET";
  private static final String REOPENED_TIMESHEET = "REOPENED_TIMESHEET";

  private final TimesheetRepository repository;
  private final TimesheetSettingsRepository timesheetSettingsRepository;
  private final TimesheetToPdfService timesheetToPdfService;
  private final FileUploadService fileUploadService;
  private final NotificationService notificationService;
  private final BillableClientRepository billableClientRepository;


  public TimesheetService(TimesheetRepository repository,
                          TimesheetSettingsRepository timesheetSettingsRepository,
                          TimesheetToPdfService timesheetToPdfService,
                          FileUploadService fileUploadService,
                          NotificationService notificationService, BillableClientRepository billableClientRepository) {
    this.repository = repository;
    this.timesheetSettingsRepository = timesheetSettingsRepository;
    this.timesheetToPdfService = timesheetToPdfService;
    this.fileUploadService = fileUploadService;
    this.notificationService = notificationService;
    this.billableClientRepository = billableClientRepository;
  }

  public Page<Timesheet> findAll(Pageable pageable) {
    return this.repository.findAll(pageable);
  }

  public Map<Integer, List<Timesheet>> findAllGroupedByYear() {
    return this.repository.findAll().stream().collect(Collectors.groupingBy(Timesheet::getYear));
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
    Timesheet ts = this.repository.findById(id).orElseThrow(()-> new RuntimeException("Timesheet %s not found".formatted(id)));
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
            .equals(newPeriod.getId())), Stream.of(newPeriod))
        .sorted(Comparator.comparing(TimesheetPeriod::getDate))
        .collect(Collectors.toList()))
      .build();
    Timesheet save = repository.save(updatedTimesheet);
    return newPeriod;
  }

  protected Timesheet defaultTimesheet(String clientId) {
    var client = this.billableClientRepository.findById(clientId)
      .orElseThrow(() -> new RuntimeException("client %s doesn't exist".formatted(clientId)));
    return Timesheet.builder()
      .name(DateTimeFormatter.ofPattern("MM/yyyy").format(LocalDate.now()))
      .yearMonth(YearMonth.now())
      .clientId(client.getId())
      .clientName(client.getName())
      .periods(new ArrayList<>())
      .build();
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
      .name("timesheet-" + id)
      .originalFilename("timesheet-" + id)
      .contentType(MediaType.APPLICATION_PDF_VALUE)
      .bytes(bytes)
      .build(), timesheet.getId(), false);
    var saved = repository.save(timesheet.toBuilder()
      .closed(true)
      .uploadId(uploadId)
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
    var saved = repository.save(timesheet.toBuilder()
      .closed(false)
      .uploadId(null)
      .build());

    this.notificationService.sendEvent(
      "Timesheet Reopened (%s)".formatted(saved.getName()),
      REOPENED_TIMESHEET, saved.getId());

  }

  public TimesheetSettings getSettings() {
    return timesheetSettingsRepository.findAll().stream().findFirst().orElseGet(TimesheetSettings.builder()::build);
  }

  public TimesheetSettings updateSettings(TimesheetSettings settings) {
    TimesheetSettings updated = this.getSettings()
      .toBuilder()
      .minHoursPerDay(settings.getMinHoursPerDay())
      .defaultProjectName(settings.getDefaultProjectName())
      .maxHoursPerDay(settings.getMaxHoursPerDay())
      .build();
    return timesheetSettingsRepository.save(updated);
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
        if (ts.isClosed()) {
          fileUploadService.deleteByCorrelationId(ts.getId());
           repository.deleteById(ts.getId());
        }
      });
  }

  public Optional<Timesheet> findById(String id) {
    return repository.findById(id);
  }
}
