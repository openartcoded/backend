package tech.artcoded.websitev2.pages.finance;

import lombok.*;

import java.math.BigDecimal;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"addedDate", "priceWhenAdded"})
@Builder(toBuilder = true)
public class Tick {
  @Builder.Default
  private Date addedDate = new Date();
  private BigDecimal priceWhenAdded;
  private String symbol;
  private String currency;
}
