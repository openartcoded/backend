package tech.artcoded.websitev2.security.oauth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.MvcRequestMatcher;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.OPTIONS;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static tech.artcoded.websitev2.security.oauth.Role.*;

@Configuration
@EnableWebSecurity
public class ResourceServerConfig {
  private final HandlerMappingIntrospector introspector;

  public ResourceServerConfig(HandlerMappingIntrospector introspector) {
    this.introspector = introspector;
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    var prometheusMatcher = new MvcRequestMatcher(introspector, "/api/actuator/prometheus/**");

    var memzagramPublicMatcher = new MvcRequestMatcher(introspector, "/api/memzagram/public/**");
    memzagramPublicMatcher.setMethod(GET);
    var memzagramStatMatcher = new MvcRequestMatcher(introspector, "/api/memzagram/_stat/**");
    memzagramStatMatcher.setMethod(POST);
    var formContactSubmitMatcher = new MvcRequestMatcher(introspector, "/api/form-contact/submit/**");
    formContactSubmitMatcher.setMethod(POST);

    var toolboxGetMatcher = new MvcRequestMatcher(introspector, "/api/toolbox/public/**");
    toolboxGetMatcher.setMethod(GET);
    var toolboxPostMatcher = new MvcRequestMatcher(introspector, "/api/toolbox/public/**");
    toolboxPostMatcher.setMethod(POST);

    var deleteMatcher = new MvcRequestMatcher(introspector, "/**");
    deleteMatcher.setMethod(DELETE);

    var optionMatcher = new MvcRequestMatcher(introspector, "/**");
    optionMatcher.setMethod(OPTIONS);

    var resourcePublicMatcher = new MvcRequestMatcher(introspector, "/api/resource/public/**");
    resourcePublicMatcher.setMethod(GET);
    var resourceDownloadMatcher = new MvcRequestMatcher(introspector, "/api/resource/download/**");
    resourceDownloadMatcher.setMethod(GET);

    var blogGetMatcher = new MvcRequestMatcher(introspector, "/api/blog/**");
    blogGetMatcher.setMethod(GET);

    var mainPageGetMatcher = new MvcRequestMatcher(introspector, "/api/main-page/**");
    mainPageGetMatcher.setMethod(GET);

    var blogPublicSearchMatcher = new MvcRequestMatcher(introspector, "/api/blog/public-search/**");
    blogPublicSearchMatcher.setMethod(POST);

    var apiPutMatcher = new MvcRequestMatcher(introspector, "/api/**");
    apiPutMatcher.setMethod(PUT);
    var apiPostMatcher = new MvcRequestMatcher(introspector, "/api/**");
    apiPostMatcher.setMethod(POST);
    var apiGetMatcher = new MvcRequestMatcher(introspector, "/api/**");
    apiGetMatcher.setMethod(GET);

    var cvAdminDownloadMatcher = new MvcRequestMatcher(introspector, "/api/cv/admin-download/**");
    cvAdminDownloadMatcher.setMethod(GET);
    var cvMatcher = new MvcRequestMatcher(introspector, "/api/cv/**");
    cvMatcher.setMethod(GET);
    var cvPublicDownloadMatcher = new MvcRequestMatcher(introspector, "/api/cv/download/**");
    cvPublicDownloadMatcher.setMethod(POST);

    http
        .csrf().disable()
        .authorizeHttpRequests()
        .requestMatchers(prometheusMatcher).hasAnyRole(PROMETHEUS.getAuthority())
        .requestMatchers(deleteMatcher).hasAnyRole(ADMIN.getAuthority())
        .requestMatchers(memzagramPublicMatcher).permitAll()
        .requestMatchers(memzagramStatMatcher).permitAll()

        .requestMatchers(formContactSubmitMatcher).permitAll()
        .requestMatchers(toolboxGetMatcher).permitAll()
        .requestMatchers(toolboxPostMatcher).permitAll()

        .requestMatchers(resourcePublicMatcher).permitAll()
        .requestMatchers(resourceDownloadMatcher)
        .hasAnyRole(ADMIN.getAuthority(), SERVICE_ACCOUNT_DOWNLOAD.getAuthority())
        .requestMatchers(blogGetMatcher).permitAll()
        .requestMatchers(blogPublicSearchMatcher).permitAll()

        .requestMatchers(mainPageGetMatcher).permitAll()

        .requestMatchers(cvAdminDownloadMatcher).hasAnyRole(ADMIN.getAuthority())
        .requestMatchers(cvMatcher).permitAll()
        .requestMatchers(cvPublicDownloadMatcher)
        .permitAll()

        .requestMatchers(apiPostMatcher).hasAnyRole(ADMIN.getAuthority())
        .requestMatchers(apiPutMatcher).hasAnyRole(ADMIN.getAuthority())
        .requestMatchers(apiGetMatcher)
        .hasAnyRole(ADMIN.getAuthority(), USER.getAuthority())
        .requestMatchers(optionMatcher)
        .permitAll()
        .anyRequest()
        .permitAll()

        .and()
        .oauth2ResourceServer()
        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()));

    return http.build();
  }

  private Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
    JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
    jwtConverter.setJwtGrantedAuthoritiesConverter(new RealmRoleConverter());
    return jwtConverter;
  }
}
