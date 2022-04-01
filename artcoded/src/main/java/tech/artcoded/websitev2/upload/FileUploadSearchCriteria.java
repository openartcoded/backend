package tech.artcoded.websitev2.upload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Document
public class FileUploadSearchCriteria {
  private String correlationId;
  private Date dateBefore;
  private Date dateAfter;
  private Boolean publicResource;
}
