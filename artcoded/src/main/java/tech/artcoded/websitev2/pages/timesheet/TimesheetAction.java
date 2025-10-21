package tech.artcoded.websitev2.pages.timesheet;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import tech.artcoded.websitev2.action.*;
import tech.artcoded.websitev2.pages.client.BillableClient;
import tech.artcoded.websitev2.pages.client.BillableClientRepository;
import tech.artcoded.websitev2.utils.helper.DateHelper;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
public class TimesheetAction implements Action {
  @Value("${application.holidays-config}")
  private Resource holidaysConfig;

  private PublicHolidays holidays;

  record PublicHoliday(short month, short day) {
  }

  record PublicHolidays(boolean calculateEasterDate, boolean calculateAscensionDate, boolean calculatePentecost,
      List<PublicHoliday> dates) {
  }

  private static final ObjectMapper MAPPER = new ObjectMapper();
  public static final String ACTION_KEY = "TIMESHEET_GENERATION";
  public static final String PARAMETER_START_DATE = "PARAMETER_START_DATE";
  public static final String PARAMETER_END_DATE = "PARAMETER_END_DATE";
  public static final String PARAMETER_CLIENT_ID = "PARAMETER_CLIENT_ID";

  private static final String DATE_PATTERN = "yyyy-MM-dd";

  private final TimesheetService service;
  private final BillableClientRepository billableClientRepository;

  public TimesheetAction(TimesheetService service, BillableClientRepository billableClientRepository) {
    this.service = service;
    this.billableClientRepository = billableClientRepository;
  }

  @PostConstruct
  @SneakyThrows
  public void loadHolidays() {
    log.info("loading holidays...");
    try (var is = holidaysConfig.getInputStream()) {
      this.holidays = MAPPER.readValue(is, PublicHolidays.class);
      log.info("holidays: {}", this.holidays.dates().stream().map(h -> "%s/%s".formatted(h.month(), h.day()))
          .collect(Collectors.joining(",")));
    }

  }

  static LocalDate getEasterDate(int year) {
    int a = year % 19;
    int b = year / 100;
    int c = year % 100;
    int d = b / 4;
    int e = b % 4;
    int f = (b + 8) / 25;
    int g = (b - f + 1) / 3;
    int h = (19 * a + b - d - g + 15) % 30;
    int i = c / 4;
    int k = c % 4;
    int l = (32 + 2 * e + 2 * i - h - k) % 7;
    int m = (a + 11 * h + 22 * l) / 451;
    int month = (h + l - 7 * m + 114) / 31; // 3 = March, 4 = April
    int day = ((h + l - 7 * m + 114) % 31) + 1;

    return LocalDate.of(year, month, day);
  }

  static LocalDate getAscensionDate(int year) {
    LocalDate easter = getEasterDate(year);
    return easter.plusDays(39);
  }

  static LocalDate getPentecostDate(int year) {
    LocalDate easter = getEasterDate(year);
    return easter.plusDays(49);
  }

  @Override
  public ActionResult run(List<ActionParameter> parameters) {
    var resultBuilder = this.actionResultBuilder(parameters);

    List<String> messages = new ArrayList<>();
    messages.add("starting timesheet generations...");

    try {
      var clientId = parameters.stream().filter(p -> PARAMETER_CLIENT_ID.equals(p.getKey()))
          .map(ActionParameter::getValue).filter(StringUtils::isNotEmpty).findFirst()
          .orElseThrow(() -> new RuntimeException("%s required".formatted(PARAMETER_CLIENT_ID)));
      var client = billableClientRepository.findById(clientId)
          .orElseThrow(() -> new RuntimeException("client not found %s".formatted(clientId)));

      LocalDate startDate = parameters.stream().filter(p -> PARAMETER_START_DATE.equals(p.getKey()))
          .map(ActionParameter::getValue).filter(StringUtils::isNotEmpty)
          .map(p -> LocalDate.parse(p, DateTimeFormatter.ofPattern(DATE_PATTERN))).findFirst()
          .orElseGet(() -> {
            messages.add("parameter start date is missing or wrongly formatted.");
            return DateHelper.getFirstDayOfCurrentMonth();
          });
      LocalDate endDate = parameters.stream().filter(p -> PARAMETER_END_DATE.equals(p.getKey()))
          .map(ActionParameter::getValue).filter(StringUtils::isNotEmpty)
          .map(p -> LocalDate.parse(p, DateTimeFormatter.ofPattern(DATE_PATTERN))).findFirst()
          .orElseGet(() -> {
            messages.add("parameter end date is missing or wrongly formatted.");
            return startDate.withDayOfMonth(1).plusMonths(1);
          });

      List<LocalDate> datesBetween = DateHelper.getDatesBetween(startDate, endDate);
      messages.add("creating timesheet...");
      datesBetween.stream()
          .collect(Collectors.groupingBy(YearMonth::from)).forEach((yearMonth, localDates) -> {
            String name = yearMonth.format(DateTimeFormatter.ofPattern("MM/yyyy"));
            Timesheet timesheet = service.findByNameAndClientId(name, client.getId())
                .orElseGet(() -> service.defaultTimesheet(clientId));
            Timesheet ts = timesheet.toBuilder().name(name).yearMonth(yearMonth).periods(Stream
                .concat(timesheet.getPeriods().stream(),
                    localDates.stream().map(DateHelper::toDate)
                        .filter(date -> timesheet.getPeriods().stream()
                            .noneMatch(p -> DateHelper.isSameDay(date, p.getDate())))
                        .map(date -> TimesheetPeriod.builder().projectName(client.getProjectName())
                            .periodType(getPeriodTypeByDay(client, date)).date(date).build()))
                .sorted(Comparator.comparing(TimesheetPeriod::getDate)).collect(Collectors.toList())).build();
            Timesheet timesheetSaved = service.saveOrUpdateTimesheet(ts);
            messages.add("timesheet created: %s".formatted(timesheetSaved.getName()));
          });

      return resultBuilder.finishedDate(new Date()).messages(messages).build();
    } catch (Exception e) {
      log.error("error: ", e);
      messages.add("error: %s".formatted(e.getMessage()));
      return resultBuilder.finishedDate(new Date()).status(StatusType.FAILURE).messages(messages).build();
    }

  }

  public PeriodType getPeriodTypeByDay(BillableClient client, Date date) {
    var dt = DateHelper.toLocalDate(date);
    var dayOfWeek = dt.getDayOfWeek();
    return switch (dayOfWeek) {
      case SATURDAY, SUNDAY -> PeriodType.WEEKEND;
      default -> {
        if (this.holidays.dates().stream()
            .anyMatch(h -> dt.withDayOfMonth(h.day()).withMonth(h.month()).equals(dt))) {
          yield PeriodType.PUBLIC_HOLIDAYS;
        } else if (this.holidays.calculateEasterDate() && getEasterDate(dt.getYear()).equals(dt)) {
          yield PeriodType.PUBLIC_HOLIDAYS;
        } else if (this.holidays.calculateAscensionDate() && getAscensionDate(dt.getYear()).equals(dt)) {
          yield PeriodType.PUBLIC_HOLIDAYS;
        } else if (this.holidays.calculatePentecost() && getPentecostDate(dt.getYear()).equals(dt)) {
          yield PeriodType.PUBLIC_HOLIDAYS;
        } else if (client.getDefaultWorkingDays().contains(dayOfWeek)) {
          yield PeriodType.WORKING_DAY;
        } else {
          yield PeriodType.AUTHORIZED_HOLIDAYS;
        }
      }
    };
  }

  @Override
  public ActionMetadata getMetadata() {
    return ActionMetadata.builder().key(ACTION_KEY).title("Timesheet Generation")
        .description("An action to create automatically a timesheet")
        .allowedParameters(List.of(ActionParameter.builder().key(PARAMETER_START_DATE)
            .parameterType(ActionParameterType.DATE_STRING).required(false)
            .description(
                "Represent the start date (inclusive); expected format: %s. Default is first day of current month."
                    .formatted(DATE_PATTERN))
            .build(),
            ActionParameter.builder().key(PARAMETER_END_DATE).parameterType(ActionParameterType.DATE_STRING)
                .required(false)
                .description(
                    "Represent the end date (exclusive); expected format: %s. Default is first day of next month."
                        .formatted(DATE_PATTERN))
                .build(),
            ActionParameter.builder().parameterType(ActionParameterType.OPTION).key(PARAMETER_CLIENT_ID)
                .options(billableClientRepository.findAll().stream()
                    .map(c -> Map.entry(c.getId(), c.getName()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
                .required(true).description("Client").build()))
        .defaultCronValue("0 30 1 1 1 ?").build();
  }

  @Override

  public String getKey() {
    return ACTION_KEY;
  }
}
