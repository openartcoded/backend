package tech.artcoded.websitev2.pages.personal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import tech.artcoded.websitev2.utils.helper.IdGenerators;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

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
  private String organizationPhoneNumber;
  private String organizationEmailAddress;
  private String ceoFullName;
  private String note;
  private String logoUploadId;
  private String signatureUploadId;
  private BigDecimal financeCharge;
  private Integer maxDaysToPay;
  @Builder.Default
  private List<Accountant> accountants = List.of();

}
