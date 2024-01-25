package tech.artcoded.websitev2.pages.mail;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Document
public class MailJob {

  @Id
  private String id;

  @Builder.Default
  private Date createdDate = new Date();

  private Date updatedDate;

  private boolean sent;

  private Date sendingDate;

  private String subject;
  private String body;
  private boolean bcc;

  @Builder.Default
  private List<String> to = new ArrayList<>();

  @Builder.Default
  private List<String> uploadIds = new ArrayList<>();
}
