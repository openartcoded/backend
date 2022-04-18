package tech.artcoded.websitev2.pages.timesheet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Transient;
import tech.artcoded.websitev2.api.helper.DateHelper;
import tech.artcoded.websitev2.api.helper.IdGenerators;

import java.text.DecimalFormat;
import java.time.DayOfWeek;
import java.time.Duration;
import java.util.Date;

import static java.util.Optional.ofNullable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class TimesheetPeriod {
  @Builder.Default
  private String id = IdGenerators.get();
  private String shortDescription;
  private Date date;
  private Date morningStartTime;
  private Date morningEndTime;
  private Date afternoonStartTime;
  private Date afternoonEndTime;
  private String projectName;

  private PeriodType periodType;

  @Transient
  public DayOfWeek getDayOfWeek() {
    return DateHelper.toLocalDate(date).getDayOfWeek();
  }

  @Transient
  public long getDuration() {
    Long durationMorning = ofNullable(morningStartTime)
      .map(DateHelper::toLocalDateTime)
      .map(localDateTime -> Duration.between(localDateTime, ofNullable(morningEndTime).map(DateHelper::toLocalDateTime)
        .orElse(localDateTime)).toMinutes())
      .orElse(0L);

    Long durationAfternoon = ofNullable(afternoonStartTime)
      .map(DateHelper::toLocalDateTime)
      .map(localDateTime -> Duration.between(localDateTime, ofNullable(afternoonEndTime).map(DateHelper::toLocalDateTime)
          .orElse(localDateTime))
        .toMinutes()).orElse(0L);

    return durationAfternoon + durationMorning;
  }

  @Transient
  public String getDurationInHours() {
    DecimalFormat df = new DecimalFormat("00");
    return (df.format(getDuration() / 60)) + ":" + (df.format(getDuration() % 60));
  }

  @Transient
  public boolean isRowFilled() {
    return afternoonEndTime!=null && afternoonStartTime!=null && morningStartTime!=null && morningEndTime!=null;
  }
}
