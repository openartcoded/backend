package tech.artcoded.websitev2.rest.util;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.springframework.web.multipart.MultipartFile;
import tech.artcoded.websitev2.api.helper.MultipartFileHelper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MockMultipartFile implements MultipartFile {
  private String name;
  private String originalFilename;
  private String contentType;
  private byte[] bytes;

  @SneakyThrows
  public static MultipartFile copy(MultipartFile file) {
    return MockMultipartFile.builder()
                            .bytes(file.getBytes())
                            .contentType(file.getContentType())
                            .name(file.getName())
                            .originalFilename(file.getOriginalFilename())
                            .build();
  }

  @Override
  public boolean isEmpty() {
    return MultipartFileHelper.isEmpty(bytes);
  }

  @Override
  public long getSize() {
    return MultipartFileHelper.getSize(bytes);
  }

  @Override
  public InputStream getInputStream() throws IOException {
    return MultipartFileHelper.getInputStream(bytes);
  }

  @Override
  public void transferTo(File dest) throws IOException, IllegalStateException {
    File temp = new File(dest, this.getName());
    FileUtils.writeByteArrayToFile(temp, this.getBytes());
  }
}
