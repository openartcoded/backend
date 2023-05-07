package tech.artcoded.websitev2.pages.dossier;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class TvaAdvancePayment implements Serializable {
  private static final long serialVersionUID = 1L;
  private Date datePaid;
  private BigDecimal advance;
}
