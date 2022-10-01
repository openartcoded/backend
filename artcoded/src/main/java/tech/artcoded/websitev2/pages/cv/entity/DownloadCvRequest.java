package tech.artcoded.websitev2.pages.cv.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import tech.artcoded.websitev2.utils.helper.IdGenerators;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Document
public class DownloadCvRequest {
  @Id
  @Builder.Default
  private String id = IdGenerators.get();
  private String email;
  private String phoneNumber;
  private String htmlContent;
  private boolean dailyRate;
  private boolean availability;
  private Date dateReceived;
}
