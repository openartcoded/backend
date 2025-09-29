package tech.artcoded.websitev2.utils.helper;

import lombok.SneakyThrows;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static java.time.temporal.TemporalAdjusters.firstDayOfYear;
import static java.time.temporal.TemporalAdjusters.lastDayOfYear;

public interface DateHelper {
  @Deprecated
  static String getCreationDateToString() {
    return ZonedDateTime.now(ZoneId.of("Europe/Paris"))
        .format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"));
  }

  static List<LocalDate> getDatesBetween(LocalDate startDate, LocalDate endDate) {
    return startDate.datesUntil(endDate)
        .collect(Collectors.toList());
  }

  static Date toDate(LocalDate dateToConvert) {
    return Date.from(dateToConvert.atTime(6, 0) // avoid bothering with zone
        .atZone(ZoneId.systemDefault())
        .toInstant());
  }

  static LocalDate toLocalDate(Date dateToConvert) {
    return dateToConvert.toInstant()
        .atZone(ZoneId.of("Europe/Paris"))
        .toLocalDate();
  }

  static boolean isSameDay(Date date1, Date date2) {
    LocalDate localDate1 = toLocalDate(date1);
    LocalDate localDate2 = toLocalDate(date2);
    return localDate1.isEqual(localDate2);
  }

  static LocalDate getFirstDayOfCurrentYear() {
    return LocalDate.now().with(firstDayOfYear());
  }

  static LocalDate getFirstDayOfCurrentMonth() {
    return LocalDate.now().withDayOfMonth(1);
  }

  static LocalDate getFirstDayOfNextMonth() {
    return getFirstDayOfCurrentMonth().plusMonths(1);
  }

  static LocalDate getLastDayOfCurrentYear() {
    return LocalDate.now().with(lastDayOfYear());
  }

  static boolean isWeekend(LocalDate dt) {
    return switch (dt.getDayOfWeek()) {
      case SATURDAY, SUNDAY -> true;
      default -> false;
    };
  }

  static boolean isWeekend(Date dt) {
    return isWeekend(toLocalDate(dt));
  }

  static LocalDateTime toLocalDateTime(Date date) {
    return date.toInstant()
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime();
  }

  static Date toDate(LocalDateTime dateToConvert) {
    return Date
        .from(dateToConvert.atZone(ZoneId.systemDefault())
            .toInstant());
  }

  static String getDateToString(Date date) {
    return ZonedDateTime.ofInstant(date.toInstant(), ZoneId.of("Europe/Paris"))
        .format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"));
  }

  static String getYYYYMMDD(Date date) {
    return ZonedDateTime.ofInstant(date.toInstant(), ZoneId.of("Europe/Paris"))
        .format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
  }

  static String getICSDate(Date date) {
    return ZonedDateTime.ofInstant(date.toInstant(), ZoneId.of("Europe/Paris"))
        .format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
  }

  @SneakyThrows
  static Date stringToDate(String date) {
    return new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").parse(date);
  }
}
