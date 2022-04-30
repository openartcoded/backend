package tech.artcoded.websitev2.security.oauth;

import org.springframework.security.core.GrantedAuthority;

public enum Role implements GrantedAuthority {
  USER,
  PROMETHEUS,
  ADMIN,
  SERVICE_ACCOUNT_DOWNLOAD;

  @Override
  public String getAuthority() {
    return this.name();
  }
}
