package tech.artcoded.websitev2.pages.timesheet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import tech.artcoded.websitev2.api.helper.IdGenerators;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Document
public class TimesheetSettings {
  @Id
  @Builder.Default
  private String id = IdGenerators.get();

  @Builder.Default
  private BigDecimal maxHoursPerDay = new BigDecimal("8");
  @Builder.Default
  private BigDecimal minHoursPerDay = new BigDecimal("8");

  @Builder.Default
  private String defaultProjectName = "Consulting";
}
