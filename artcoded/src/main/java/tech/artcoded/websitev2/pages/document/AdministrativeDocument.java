package tech.artcoded.websitev2.pages.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import tech.artcoded.websitev2.utils.helper.IdGenerators;

import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Document
public class AdministrativeDocument {
  @Id
  @Builder.Default
  private String id = IdGenerators.get();
  @Builder.Default
  private Date dateCreation = new Date();
  @Builder.Default
  private Date updatedDate = new Date();
  private Date date;
  private String attachmentId;
  @Builder.Default
  private List<String> tags = List.of();
  private String title;
  private String description;
  @Builder.Default
  private boolean locked = false;
  @Builder.Default
  private boolean bookmarked = false;
}
