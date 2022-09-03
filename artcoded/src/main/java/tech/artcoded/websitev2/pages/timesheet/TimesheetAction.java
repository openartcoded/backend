package tech.artcoded.websitev2.pages.timesheet;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import tech.artcoded.websitev2.action.*;
import tech.artcoded.websitev2.api.helper.DateHelper;
import tech.artcoded.websitev2.pages.client.BillableClientRepository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
public class TimesheetAction implements Action {
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

  @Override
  public ActionResult run(List<ActionParameter> parameters) {
    var resultBuilder = this.actionResultBuilder(parameters);

    List<String> messages = new ArrayList<>();
    messages.add("starting timesheet generations...");

    try {
      var clientId = parameters.stream().filter(p-> PARAMETER_CLIENT_ID.equals(p.getKey()))
        .map(ActionParameter::getValue)
        .filter(StringUtils::isNotEmpty)
        .findFirst()
        .orElseThrow(()-> new RuntimeException("%s required".formatted(PARAMETER_CLIENT_ID)))
        ;

      LocalDate startDate = parameters.stream().filter(p -> PARAMETER_START_DATE.equals(p.getKey()))
        .map(ActionParameter::getValue)
        .filter(StringUtils::isNotEmpty)
        .map(p -> LocalDate.parse(p, DateTimeFormatter.ofPattern(DATE_PATTERN)))
        .findFirst().orElseGet(() -> {
          messages.add("parameter start date is missing or wrongly formatted.");
          return DateHelper.getFirstDayOfCurrentMonth();
        });
      LocalDate endDate = parameters.stream().filter(p -> PARAMETER_END_DATE.equals(p.getKey()))
        .map(ActionParameter::getValue)
        .filter(StringUtils::isNotEmpty)
        .map(p -> LocalDate.parse(p, DateTimeFormatter.ofPattern(DATE_PATTERN)))
        .findFirst().orElseGet(() -> {
          messages.add("parameter end date is missing or wrongly formatted.");
          return startDate.withDayOfMonth(1).plusMonths(1);
        });

      List<LocalDate> datesBetween = DateHelper.getDatesBetween(startDate, endDate);
      messages.add("creating timesheet...");
      datesBetween.stream().collect(Collectors.groupingBy(YearMonth::from))
        .forEach((yearMonth, localDates) -> {
          String name = yearMonth.format(DateTimeFormatter.ofPattern("MM/yyyy"));
          Timesheet timesheet = service.findByNameAndClientId(name,clientId).orElseGet(()-> service.defaultTimesheet(clientId));
          Timesheet ts = timesheet.toBuilder().name(name)
            .yearMonth(yearMonth)
            .periods(
              Stream.concat(timesheet.getPeriods().stream(),
                  localDates.stream()
                    .map(DateHelper::toDate)
                    .filter(date -> timesheet.getPeriods()
                      .stream()
                      .noneMatch(p -> DateHelper.isSameDay(date, p.getDate())))
                    .map(date -> TimesheetPeriod.builder()
                      .projectName(service.getSettings()
                        .getDefaultProjectName())
                      .periodType(DateHelper.isWeekend(date) ? PeriodType.WEEKEND:PeriodType.WORKING_DAY)
                      .date(date).build()))
                .sorted(Comparator.comparing(TimesheetPeriod::getDate))
                .collect(Collectors.toList())
            ).build();
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

  @Override
  public ActionMetadata getMetadata() {
    return ActionMetadata.builder()
      .key(ACTION_KEY)
      .title("Timesheet Generation")
      .description("An action to create automatically a timesheet")
      .allowedParameters(List.of(
        ActionParameter.builder()
          .key(PARAMETER_START_DATE)
          .parameterType(ActionParameterType.DATE_STRING)
          .required(false)
          .description("Represent the start date (inclusive); expected format: %s. Default is first day of current month.".formatted(DATE_PATTERN))
          .build(),
        ActionParameter.builder()
          .key(PARAMETER_END_DATE)
          .parameterType(ActionParameterType.DATE_STRING)
          .required(false)
          .description("Represent the end date (exclusive); expected format: %s. Default is first day of next month.".formatted(DATE_PATTERN))
          .build(),
        ActionParameter.builder()
          .parameterType(ActionParameterType.OPTION)
          .key(PARAMETER_CLIENT_ID)
          .options(billableClientRepository.findAll().stream().map(c -> Map.entry(c.getId(), c.getName())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
          .required(true)
          .description("Client").build()
      ))
      .defaultCronValue("0 30 1 1 1 ?")
      .build();
  }

  @Override

  public String getKey() {
    return ACTION_KEY;
  }
}
