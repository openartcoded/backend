package tech.artcoded.websitev2.pages.timesheet;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;
import tech.artcoded.websitev2.utils.helper.IdGenerators;

import java.text.DecimalFormat;
import java.time.Month;
import java.time.YearMonth;
import java.util.Date;
import java.util.List;

import static java.util.Optional.ofNullable;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Document
public class Timesheet implements Comparable<YearMonth> {
  @Id
  @Builder.Default
  private String id = IdGenerators.get();
  @Builder.Default
  private Date dateCreation = new Date();

  private String name;

  @Builder.Default
  private List<TimesheetPeriod> periods = List.of();

  private boolean closed;

  private String uploadId;

  private String invoiceId;
  private String clientId;
  private String clientName;

  private TimesheetSettings settings;

  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  private YearMonth yearMonth;

  @Transient
  public int getYear() {
    return yearMonth.getYear();
  }

  @Transient
  public int getMonth() {
    return yearMonth.getMonthValue();
  }

  @Transient
  public Month getMonthEnum() {
    return yearMonth.getMonth();
  }

  @Override
  public int compareTo(YearMonth yearMonthToCompare) {
    return this.getYearMonth().compareTo(yearMonthToCompare);
  }

  @Transient
  public long getNumberOfWorkingDays() {
    return periods.stream().filter(timesheetPeriod -> PeriodType.WORKING_DAY.equals(timesheetPeriod.getPeriodType()))
        .count();
  }

  @Transient
  public long getNumberOfMinutesWorked() {
    return periods.stream().filter(timesheetPeriod -> PeriodType.WORKING_DAY.equals(timesheetPeriod.getPeriodType()))
        .mapToLong(TimesheetPeriod::getDuration).sum();
  }

  @Transient
  public String getNumberOfHoursWorked() {
    DecimalFormat df = new DecimalFormat("00");
    return (df.format(getNumberOfMinutesWorked() / 60)) + ":" + (df.format(getNumberOfMinutesWorked() % 60));
  }

  @Transient
  public BigDecimal getNumberOfWorkingHours() {
    return new BigDecimal(0).add(new BigDecimal(getNumberOfMinutesWorked()))
        .divide(new BigDecimal(60), MathContext.DECIMAL128).setScale(0, RoundingMode.DOWN);
  }

  @Transient
  public String getClientNameOrNA() {
    return ofNullable(this.clientName).filter(StringUtils::isNotEmpty).orElse("N/A");
  }
}
