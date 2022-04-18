package tech.artcoded.websitev2.api.helper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static tech.artcoded.websitev2.api.func.CheckedSupplier.toSupplier;

/**
 * @author Nordine Bittich
 */
@FunctionalInterface
public interface ObjectMapperWrapper {
  ObjectMapper mapper();

  Logger LOG = LoggerFactory.getLogger(ObjectMapperWrapper.class.getName());

  default String serialize(Object object) {
    return toSupplier(() -> mapper().writeValueAsString(object)).get();
  }

  default <T> boolean instanceOf(String value, Class<T> tClass) {
    // todo refactor
    try {
      Optional<T> deserialize = deserialize(value, tClass);
      return deserialize.isPresent();
    } catch (Exception e) {
      return false;
    }
  }

  default <T> Optional<T> deserialize(String value, Class<T> tClass) {
    try {
      return Optional.of(mapper().readValue(value, tClass));
    } catch (Exception e) {
      LOG.debug("error while deserialize -> " + tClass, e);
      return Optional.empty();
    }
  }

  /**
   * if any error occurs during the process of deserialization or if the object is null, throws a
   * runtime exception
   */
  default <T> T deserializeStrict(String value, Class<T> tClass) {
    try {
      return Optional.of(mapper().readValue(value, tClass))
        .orElseThrow(
          () ->
            new RuntimeException(
              String.format(
                "cannot deserialize class %s with value %s", tClass.getName(), value)));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  default <T> T deserialize(InputStream value, Class<T> tClass) {
    return toSupplier(() -> mapper().readValue(value, tClass)).get();
  }

  default List<String> serializeList(List<Object> list) {
    return list.stream().map(this::serialize).collect(Collectors.toList());
  }

  default <T> List<T> deserializeList(List<String> list, Class<T> tClass) {
    return list.stream()
      .filter(Objects::nonNull)
      .map(o -> deserialize(o, tClass))
      .filter(Optional::isPresent)
      .map(Optional::get)
      .collect(Collectors.toList());
  }
}
