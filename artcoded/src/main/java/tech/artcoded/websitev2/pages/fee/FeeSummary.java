package tech.artcoded.websitev2.pages.fee;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class FeeSummary implements Serializable {

  private static final long serialVersionUID = 1L;
  private String tag;
  @Builder.Default
  private BigDecimal totalHVAT = BigDecimal.ZERO;
  @Builder.Default
  private BigDecimal totalVAT = BigDecimal.ZERO;

}
