package tech.artcoded.websitev2.pages.personal;

import java.beans.Transient;
import java.io.Serializable;
import java.security.Principal;
import java.util.List;
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
  private List<String> authorities;

  @Transient
  public static User fromPrincipal(Principal principal) {
    JwtAuthenticationToken user = (JwtAuthenticationToken) principal;
    var userRoles = user.getAuthorities()
        .stream()
        .map(a -> a.getAuthority().replaceAll("ROLE_", ""))
        .peek(a -> log.debug("user has roles {}", a))
        .toList();
    return User.builder()
        .username(user.getName())
        .authorities(userRoles)
        .build();
  }
}
