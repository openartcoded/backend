package tech.artcoded.websitev2.pages.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.apache.commons.lang3.StringUtils;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;

import tech.artcoded.websitev2.domain.common.RateType;
import tech.artcoded.websitev2.utils.helper.IdGenerators;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.Date;
import java.util.List;
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
    private BigDecimal rate;
    private RateType rateType;
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
