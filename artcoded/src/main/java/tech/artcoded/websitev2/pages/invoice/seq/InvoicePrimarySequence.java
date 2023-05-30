package tech.artcoded.websitev2.pages.invoice.seq;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.artcoded.websitev2.utils.helper.IdGenerators;

@Document
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoicePrimarySequence {

  @Id
  @Builder.Default
  private String id = IdGenerators.get();
  private long seq;
}
