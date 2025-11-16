
package tech.artcoded.websitev2.pages.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import tech.artcoded.websitev2.utils.helper.IdGenerators;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;
import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Document
public class Channel {
  @Id
  @Builder.Default
  private String id = IdGenerators.get();

  @Builder.Default
  private List<String> subscribers = new ArrayList<>();

  @Builder.Default
  private List<Message> messages = new ArrayList<>();

  @Builder.Default
  private Date creationDate = new Date();

  private String correlationId;

  private Date updatedDate;

  public record Message(String id, Date creationDate, String emailFrom, String content, List<String> attachmentIds,
      boolean read) {
  }
}
