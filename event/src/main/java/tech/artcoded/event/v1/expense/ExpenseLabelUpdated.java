package tech.artcoded.event.v1.expense;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.artcoded.event.IEvent;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ExpenseLabelUpdated implements IEvent {
  private String expenseId;
  private String label;
  private BigDecimal priceHVat;
  private BigDecimal vat;

  @Override
  public Version getVersion() {
    return Version.V1;
  }
}


