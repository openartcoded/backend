package tech.artcoded.websitev2.pages.invoice;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import tech.artcoded.websitev2.api.helper.IdGenerators;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class CurrentBillTo {

  @Id
  @Builder.Default
  private String id = IdGenerators.get();
  private BillTo billTo;
  private int maxDaysToPay;
  private BigDecimal rate;
  private InvoicingType rateType;
  private String projectName;
}
