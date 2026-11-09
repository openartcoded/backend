package tech.artcoded.websitev2.utils.helper;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Shared Jackson 3 mapper for code that doesn't use the Spring managed one. Keeps the Jackson 2 behavior the
 * application was written against: a missing or null value for a primitive (boolean, int...) becomes its default
 * instead of failing.
 */
public final class Json {
    public static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();

    private Json() {
    }
}
