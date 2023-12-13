package tech.artcoded.websitev2.pages.mail;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MailRequest {
  @Builder.Default
  private List<String> to = List.of();
  private String subject;
  private String body;
  private boolean bcc;
  @Builder.Default
  private List<String> uploadIds = List.of();

  private java.util.Date sendingDate;


}
