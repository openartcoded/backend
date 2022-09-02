package tech.artcoded.websitev2.pages.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import tech.artcoded.websitev2.api.helper.IdGenerators;
import tech.artcoded.websitev2.domain.common.RateType;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class BillableClient {
  @Id
  @Builder.Default
  private String id = IdGenerators.get();
  private int maxDaysToPay;
  private BigDecimal rate;
  private RateType rateType;
  private String projectName;
  private String vatNumber;
  private String address;
  private String city;
  private String name;
  private String emailAddress;
  private String phoneNumber;
  private ContractStatus contractStatus;
  private Date startDate;
  private Date endDate;
  private List<String> documentIds;

}
