package tech.artcoded.websitev2.pages.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;

import tech.artcoded.websitev2.domain.common.RateType;
import tech.artcoded.websitev2.utils.helper.IdGenerators;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class BillableClient {
    @Id
    @Builder.Default
    private String id = IdGenerators.get();
    private int maxDaysToPay;
    private BigDecimal taxRate;
    private String nature;
    /**
     * @deprecated kept in sync with the first (default) entry of {@link #rates}
     */
    private BigDecimal rate;
    /**
     * @deprecated kept in sync with the first (default) entry of {@link #rates}
     */
    private RateType rateType;
    /**
     * One or many rates. The first one is the default rate.
     */
    @Builder.Default
    private List<ClientRate> rates = new ArrayList<>();
    private String projectName;
    private String vatNumber;
    private String address;
    private String city;
    private String name;
    private String emailAddress;
    private String countryCode;
    private String phoneNumber;
    private ContractStatus contractStatus;
    private Date startDate;
    private Date endDate;
    @Builder.Default
    private List<String> documentIds = List.of();

    // wheiter it was imported from an old system
    private boolean imported;
    private Date importedDate;
    private boolean student; // 2025-10-31 14:42

    private List<DayOfWeek> defaultWorkingDays;

    /**
     * The rates of the client. Clients not migrated yet (only legacy rate/rateType)
     * are exposed as a single default rate.
     */
    @Transient
    @JsonIgnore
    public List<ClientRate> getRatesOrDefault() {
        var current = Optional.ofNullable(rates).orElseGet(List::of).stream().filter(Objects::nonNull).toList();
        if (!current.isEmpty()) {
            return current;
        }
        if (rate == null || rateType == null) {
            return List.of();
        }
        return List.of(ClientRate.builder().rate(rate).rateType(rateType).build());
    }

    /**
     * Cleans the rates received from the outside: removes incomplete entries, sets
     * missing ids and trims labels. When no rate is given, the legacy
     * rate/rateType is used. The first rate is mirrored in the legacy fields.
     */
    public static List<ClientRate> normalizeRates(List<ClientRate> received, BigDecimal legacyRate,
            RateType legacyRateType) {
        var normalized = new ArrayList<ClientRate>();
        for (var r : Optional.ofNullable(received).orElseGet(List::of)) {
            if (r == null || r.getRate() == null || r.getRateType() == null) {
                continue;
            }
            normalized.add(r.toBuilder().id(StringUtils.defaultIfBlank(r.getId(), IdGenerators.get()))
                    .label(StringUtils.trimToNull(r.getLabel())).build());
        }
        if (normalized.isEmpty() && legacyRate != null && legacyRateType != null) {
            normalized.add(ClientRate.builder().rate(legacyRate).rateType(legacyRateType).build());
        }
        return normalized;
    }

    @Transient
    public String getCompanyNumber() {
        return getCleanVatNumber().replaceFirst("^[a-zA-Z]{0,2}", "");
    }

    @Transient
    public String getCleanVatNumber() {
        return StringUtils.leftPad(Optional.ofNullable(vatNumber).orElse("").replace(".", "").replace(" ", ""), 10,
                "0");

    }

    @Transient
    public String getUBLRateType() {
        return switch (rateType) {
            case RateType.HOURS -> "HUR";
            case RateType.DAYS -> "DAY";
        };
    }
}
