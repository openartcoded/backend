package tech.artcoded.websitev2.pages.immo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Deprecated(forRemoval = true)
public class ImmoFilter {
  @Id
  private String id;

  private String rawJson;

  @Builder.Default
  private Date dateCreation = new Date();
}
