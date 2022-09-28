package tech.artcoded.websitev2.upload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import static tech.artcoded.websitev2.api.helper.DateHelper.getCreationDateToString;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class FileUploadMetadata {


  private String creationDate;
  private String contentType;
  private String originalFilename;
  private String correlationId;
  private String name;
  private long size;
  private boolean publicResource;

  public static FileUploadMetadata newUpload(
    MultipartFile file, String correlationId, boolean isPublic) {
    FileUploadMetadata upload = new FileUploadMetadata();
    upload.setContentType(file.getContentType());
    upload.setOriginalFilename(file.getOriginalFilename());
    upload.setName(file.getOriginalFilename());
    upload.setSize(file.getSize());
    upload.setCreationDate(getCreationDateToString());
    upload.setPublicResource(isPublic);
    upload.setCorrelationId(correlationId);
    return upload;
  }
}
