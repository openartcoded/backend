package tech.artcoded.websitev2.pages.personal;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.apache.commons.lang3.StringUtils;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;
import tech.artcoded.websitev2.utils.helper.IdGenerators;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Document
public class PersonalInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    @Id
    @Builder.Default
    private String id = IdGenerators.get();
    @Builder.Default
    private Date dateCreation = new Date();
    private Date updatedDate;
    private String organizationName;
    private String vatNumber;
    private String organizationAddress;
    private String organizationPostCode;
    private String organizationCity;
    private String organizationBankAccount;
    private String organizationBankBIC;
    private String countryCode;
    private String organizationPhoneNumber;
    private String organizationEmailAddress;
    private String ceoFullName;
    private String note;
    private String logoUploadId;
    private String initialUploadId;
    private String signatureUploadId;
    private BigDecimal financeCharge;
    private Integer maxDaysToPay;
    private boolean demoMode;
    @Builder.Default
    private List<Accountant> accountants = List.of();

    @Transient
    public String getCompanyNumber() {
        return getCleanVatNumber().replaceFirst("^[a-zA-Z]{0,2}", "");
    }

    @Transient
    public String getCleanVatNumber() {
        return StringUtils.leftPad(Optional.ofNullable(vatNumber).orElse("").replace(".", "").replace(" ", ""), 10,
                "0");

    }
}
