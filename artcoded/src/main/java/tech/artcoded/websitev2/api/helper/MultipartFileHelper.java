package tech.artcoded.websitev2.api.helper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public interface MultipartFileHelper {

  static boolean isEmpty(byte[] bytes) {
    return bytes == null || bytes.length == 0;
  }

  static long getSize(byte[] bytes) {
    return bytes.length;
  }

  static InputStream getInputStream(byte[] bytes) throws IOException {
    return new ByteArrayInputStream(bytes);
  }
}