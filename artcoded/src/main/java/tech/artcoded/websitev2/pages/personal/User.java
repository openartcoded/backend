package tech.artcoded.websitev2.pages.personal;

import java.beans.Transient;
import java.io.Serializable;
import java.security.Principal;
import java.util.List;
import java.util.Optional;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Slf4j
public class User implements Serializable {
    private String username;
    private String email;
    private List<String> authorities;

    @Transient
    public static User fromPrincipal(Principal principal) {
        JwtAuthenticationToken user = (JwtAuthenticationToken) principal;
        log.debug("token {}", user.getToken());
        var email = Optional.ofNullable(user.getToken()).flatMap(t -> Optional.ofNullable(t.getClaim("email")))
                .map(o -> o.toString()).orElse(null);
        var username = Optional.ofNullable(user.getToken())
                .flatMap(t -> Optional.ofNullable(t.getClaim("preferred_username"))).map(Object::toString)
                .orElse(user.getName());

        var userRoles = user.getAuthorities().stream().map(a -> a.getAuthority().replaceAll("ROLE_", ""))
                .peek(a -> log.debug("user has roles {}", a)).toList();
        return User.builder().email(email).username(username).authorities(userRoles).build();
    }
}
