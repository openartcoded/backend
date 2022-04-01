package tech.artcoded.websitev2.pages.contact;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import tech.artcoded.websitev2.api.helper.IdGenerators;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Document
public class FormContact {

  @Id
  @Builder.Default
  private String id = IdGenerators.get();
  @Builder.Default
  private Date creationDate = new Date();
  private String fullName;
  private String bestTimeToCall;
  private String email;
  private String phoneNumber;
  private String subject;
  private String body;
}
