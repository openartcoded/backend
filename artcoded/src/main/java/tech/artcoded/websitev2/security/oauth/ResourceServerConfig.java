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

import static tech.artcoded.websitev2.security.oauth.Role.*;

@Configuration
@EnableWebSecurity
// @EnableGlobalMethodSecurity(jsr250Enabled = true)
public class ResourceServerConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf().disable()
                .authorizeHttpRequests()
                .requestMatchers("/api/actuator/prometheus/**")
                .hasAnyRole(PROMETHEUS.getAuthority())
                .requestMatchers(HttpMethod.DELETE, "/**")
                .hasAnyRole(ADMIN.getAuthority())
                .requestMatchers(HttpMethod.GET, "/api/memzagram/public/**")
                .permitAll()
                .requestMatchers(HttpMethod.POST, "/api/memzagram/_stat/**")
                .permitAll()

                .requestMatchers(HttpMethod.POST, "/api/form-contact/submit/**")
                .permitAll()
                .requestMatchers(HttpMethod.GET, "/api/toolbox/public/**")
                .permitAll()
                .requestMatchers(HttpMethod.POST, "/api/toolbox/public/**")
                .permitAll()

                .requestMatchers(HttpMethod.GET, "/api/resource/public/**")
                .permitAll()
                .requestMatchers(HttpMethod.GET, "/api/resource/download/**")
                .hasAnyRole(ADMIN.getAuthority(), SERVICE_ACCOUNT_DOWNLOAD.getAuthority())
                .requestMatchers(HttpMethod.GET, "/api/blog/**")
                .permitAll()
                .requestMatchers(HttpMethod.POST, "/api/blog/public-search/**")
                .permitAll()
                .requestMatchers(HttpMethod.GET, "/api/main-page/**")
                .permitAll()
                .requestMatchers(HttpMethod.GET, "/api/cv/admin-download/**")
                .hasAnyRole(ADMIN.getAuthority())
                .requestMatchers(HttpMethod.GET, "/api/cv/**")
                .permitAll()
                .requestMatchers(HttpMethod.POST, "/api/cv/download/**")
                .permitAll()
                .requestMatchers(HttpMethod.POST, "/api/**")
                .hasAnyRole(ADMIN.getAuthority())
                .requestMatchers(HttpMethod.PUT, "/api/**")
                .hasAnyRole(ADMIN.getAuthority())
                .requestMatchers(HttpMethod.GET, "/api/**")
                .hasAnyRole(ADMIN.getAuthority(), USER.getAuthority())
                .requestMatchers(HttpMethod.OPTIONS, "/**")
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
