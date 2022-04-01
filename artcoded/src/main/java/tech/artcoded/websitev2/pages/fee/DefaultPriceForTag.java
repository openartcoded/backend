package tech.artcoded.websitev2.pages.fee;

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
public class DefaultPriceForTag {
  @Id
  @Builder.Default
  private String id = IdGenerators.get();
  @Builder.Default
  private Date dateCreation = new Date();
  @Builder.Default
  private Date updatedDate = new Date();
  private Tag tag;
  private BigDecimal priceHVAT;
  private BigDecimal vat;
}
