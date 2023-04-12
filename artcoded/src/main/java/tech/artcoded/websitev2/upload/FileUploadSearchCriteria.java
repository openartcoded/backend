package tech.artcoded.websitev2.upload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class FileUploadSearchCriteria {
  private String correlationId;
  private Date dateBefore;
  private Date dateAfter;
  private Boolean publicResource;
  private String originalFilename;
}
