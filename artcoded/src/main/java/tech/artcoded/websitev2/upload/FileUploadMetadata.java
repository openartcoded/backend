package tech.artcoded.websitev2.upload;

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
public class FileUploadMetadata {


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


  private String creationDate;

  private String contentType;
  private String originalFilename;
  private String correlationId;
  private String name;
  private long size;
  private boolean publicResource;
}
