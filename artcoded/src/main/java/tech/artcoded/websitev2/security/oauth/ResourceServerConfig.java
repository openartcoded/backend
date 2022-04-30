package tech.artcoded.websitev2.security.oauth;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import static tech.artcoded.websitev2.security.oauth.Role.*;


@Configuration
@EnableWebSecurity
//@EnableGlobalMethodSecurity(jsr250Enabled = true)
public class ResourceServerConfig extends WebSecurityConfigurerAdapter {

  @Override
  protected void configure(HttpSecurity http) throws Exception {
    http
      .csrf().disable()
      .authorizeRequests()
      .antMatchers("/api/actuator/prometheus/**")
      .hasAnyRole(PROMETHEUS.getAuthority())
      .antMatchers(HttpMethod.DELETE, "/**")
      .hasAnyRole(ADMIN.getAuthority())
      .antMatchers(HttpMethod.GET, "/api/memzagram/public/**")
      .permitAll()
      .antMatchers(HttpMethod.POST, "/api/memzagram/_stat/**")
      .permitAll()

      .antMatchers(HttpMethod.POST, "/api/form-contact/submit/**")
      .permitAll()
      .antMatchers(HttpMethod.GET, "/api/toolbox/public/**")
      .permitAll()
      .antMatchers(HttpMethod.POST, "/api/toolbox/public/**")
      .permitAll()

      .antMatchers(HttpMethod.GET, "/api/resource/public/**")
      .permitAll()
      .antMatchers(HttpMethod.GET, "/api/resource/download/**")
      .hasAnyRole(ADMIN.getAuthority(), SERVICE_ACCOUNT_DOWNLOAD.getAuthority())
      .antMatchers(HttpMethod.GET, "/api/blog/**")
      .permitAll()
      .antMatchers(HttpMethod.POST, "/api/blog/public-search/**")
      .permitAll()
      .antMatchers(HttpMethod.GET, "/api/main-page/**")
      .permitAll()
      .antMatchers(HttpMethod.GET, "/api/cv/admin-download/**")
      .hasAnyRole(ADMIN.getAuthority())
      .antMatchers(HttpMethod.GET, "/api/cv/**")
      .permitAll()
      .antMatchers(HttpMethod.POST, "/api/cv/download/**")
      .permitAll()
      .antMatchers(HttpMethod.POST, "/api/**")
      .hasAnyRole(ADMIN.getAuthority())
      .antMatchers(HttpMethod.PUT, "/api/**")
      .hasAnyRole(ADMIN.getAuthority())
      .antMatchers(HttpMethod.GET, "/api/**")
      .hasAnyRole(ADMIN.getAuthority(), USER.getAuthority())
      .antMatchers(HttpMethod.OPTIONS, "/**")
      .permitAll()
      .anyRequest()
      .permitAll()

      .and()
      .oauth2ResourceServer()
      .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
    ;
  }

  private Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
    JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
    jwtConverter.setJwtGrantedAuthoritiesConverter(new RealmRoleConverter());
    return jwtConverter;
  }
}
