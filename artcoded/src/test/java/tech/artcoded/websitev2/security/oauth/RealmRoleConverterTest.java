package tech.artcoded.websitev2.security.oauth;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class RealmRoleConverterTest {
    private final RealmRoleConverter converter = new RealmRoleConverter();

    private Jwt jwt(Map<String, Object> claims) {
        var builder = Jwt.withTokenValue("token").header("alg", "none")
                .issuedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .expiresAt(Instant.parse("2026-01-01T01:00:00Z"));
        claims.forEach(builder::claim);
        return builder.build();
    }

    @Test void missingRealmClaimProducesNoAuthorities() {
        assertThat(converter.convert(jwt(Map.of("sub", "user")))).isEmpty();
    }

    @Test void realmRolesBecomeUppercaseSpringAuthorities() {
        var token = jwt(Map.of("realm_access", Map.of("roles", List.of("admin", "reader"))));
        assertThat(converter.convert(token)).extracting("authority")
                .containsExactly("ROLE_ADMIN", "ROLE_READER");
    }

    @Test void emptyRoleListProducesNoAuthorities() {
        assertThat(converter.convert(jwt(Map.of("realm_access", Map.of("roles", List.of()))))).isEmpty();
    }
}
