package tech.artcoded.websitev2.upload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;
import tech.artcoded.websitev2.utils.helper.IdGenerators;

import java.util.Date;

import static tech.artcoded.websitev2.utils.helper.DateHelper.getDateToString;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document
public class FileUpload {
  @Id
  @Builder.Default
  private String id = IdGenerators.get();

  private Date creationDate;
  private String contentType;
  private String originalFilename;
  private String correlationId;
  private String extension;
  private String name;
  private long size;
  private boolean publicResource;

  @Transient
  public String getCreationDateString() {
    return getDateToString(this.creationDate);
  }
}
