package tech.artcoded.websitev2.pages.personal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import tech.artcoded.websitev2.api.helper.IdGenerators;

import java.math.BigDecimal;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Document
public class PersonalInfo {
  @Id
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
  private String organizationPhoneNumber;
  private String organizationEmailAddress;
  private String ceoFullName;
  private String note;
  private String logoUploadId;
  private String signatureUploadId;
  private BigDecimal financeCharge;
  private Integer maxDaysToPay;


}
