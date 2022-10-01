package tech.artcoded.websitev2.utils.helper;

import java.util.UUID;
import java.util.function.Supplier;

public interface IdGenerators {
  Supplier<String> UUID_SUPPLIER = () -> UUID.randomUUID().toString();

  static String get() {
    return UUID_SUPPLIER.get();
  }


}
