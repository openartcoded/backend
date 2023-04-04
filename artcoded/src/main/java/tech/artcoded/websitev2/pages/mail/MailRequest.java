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
  private List<String> to;
  private String subject;
  private String body;
  private boolean bcc;
  @Builder.Default
  private List<String> uploadIds = List.of();
}
