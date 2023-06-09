package tech.artcoded.websitev2.pages.dossier;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.artcoded.websitev2.pages.fee.Fee;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class DossierSummary implements Serializable {
  private static final long serialVersionUID = 1L;
  private String name;
  @JsonIgnore
  private Dossier dossier;
  @JsonIgnore
  private Map<String, List<Fee>> totalExpensesPerTag;
  private BigDecimal totalEarnings;

  public Date getClosedDate() {
    return this.dossier.getClosedDate();
  }

  public BigDecimal getTotalExpenses() {
    return getComputedTotalExpensesPerTag().values().stream()
        .reduce(new BigDecimal(0), BigDecimal::add)
        .setScale(2, RoundingMode.DOWN);
  }

  public Map<String, BigDecimal> getComputedTotalExpensesPerTag() {
    return ofNullable(totalExpensesPerTag)
        .stream()
        .map(Map::entrySet)
        .flatMap(Collection::stream)
        .map(e -> Map.entry(e.getKey(), e.getValue().stream().map(Fee::getPriceHVAT)
            .reduce(new BigDecimal(0), BigDecimal::add).setScale(2, RoundingMode.DOWN)))

        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}
