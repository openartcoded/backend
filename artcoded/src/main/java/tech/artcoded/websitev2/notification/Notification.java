package tech.artcoded.websitev2.notification;

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
@Document
public class Notification {
  @Id
  private String id;

  @Builder.Default
  private Date receivedDate = new Date();

  private boolean seen;
  private String title;
  private String type;

  private String correlationId;
}
