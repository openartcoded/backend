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
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Document
public class Fee {


  @Id
  @Builder.Default
  private String id = IdGenerators.get();
  @Builder.Default
  private Date dateCreation = new Date();
  @Builder.Default
  private Date updatedDate = new Date();
  private Date date;
  private String subject;
  private String body;
  private List<String> attachmentIds;
  private boolean archived;
  private Date archivedDate;
  private String tag;
  private BigDecimal priceHVAT;
  private BigDecimal vat;

  public BigDecimal getPriceTot() {
    return Stream.of(priceHVAT, vat)
                 .filter(Objects::nonNull)
                 .reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
