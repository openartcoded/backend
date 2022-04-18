package tech.artcoded.websitev2.pages.fee;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class FeeSummary {

  private String tag;
  @Builder.Default
  private BigDecimal totalHVAT = BigDecimal.ZERO;
  @Builder.Default
  private BigDecimal totalVAT = BigDecimal.ZERO;

}
