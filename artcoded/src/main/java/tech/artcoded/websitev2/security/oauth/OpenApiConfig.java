package tech.artcoded.websitev2.security.oauth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "application.openapi.enabled", havingValue = "true")
public class OpenApiConfig {

}
