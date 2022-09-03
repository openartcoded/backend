package tech.artcoded.websitev2.pages.timesheet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class TimesheetSettings {
  @Builder.Default
  private BigDecimal maxHoursPerDay = new BigDecimal("8");
  @Builder.Default
  private BigDecimal minHoursPerDay = new BigDecimal("8");

  @Builder.Default
  private String defaultProjectName = "Consulting";
}
