package tech.artcoded.websitev2.api.helper;

import java.util.Optional;

public interface FileHelper {
  static Optional<String> getExtension(String filename) {
    return Optional.ofNullable(filename)
      .filter(f -> f.contains("."))
      .map(f -> f.substring(filename.lastIndexOf(".") + 1));
  }
}
