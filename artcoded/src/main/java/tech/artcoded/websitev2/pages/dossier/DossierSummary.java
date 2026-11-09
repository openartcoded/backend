package tech.artcoded.websitev2.pages.dossier;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.artcoded.websitev2.pages.fee.Fee;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
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
        return ofNullable(this.dossier).map(Dossier::getClosedDate).orElse(null);
    }

    public BigDecimal getTotalExpenses() {
        return getComputedTotalExpensesPerTag().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2,
                RoundingMode.DOWN);
    }

    /**
     * Total (excluding VAT) per tag, sorted by tag. Expenses not yet priced (priceHVAT still null) count as zero
     * instead of failing the whole summary.
     */
    public Map<String, BigDecimal> getComputedTotalExpensesPerTag() {
        return ofNullable(totalExpensesPerTag).orElseGet(Map::of).entrySet().stream().filter(e -> e.getKey() != null)
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> ofNullable(e.getValue()).orElseGet(List::of).stream().filter(Objects::nonNull)
                                .map(Fee::getPriceHVAT).filter(Objects::nonNull)
                                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.DOWN),
                        BigDecimal::add, TreeMap::new));
    }
}
