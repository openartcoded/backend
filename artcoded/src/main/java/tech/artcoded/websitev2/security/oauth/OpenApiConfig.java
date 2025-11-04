package tech.artcoded.websitev2.security.oauth;

import tech.artcoded.websitev2.pages.mail.MailJobRepository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import lombok.extern.slf4j.Slf4j;

// 2025-10-17 21:56 experiment: swagger
@Configuration
@ConditionalOnProperty(name = "application.openapi.enabled", havingValue = "true")

@OpenAPIDefinition(info = @Info(title = "Artcoded", version = "1.0.0", license = @License(name = "MIT")), security = {
        @SecurityRequirement(name = "bearerAuth") })
@SecurityScheme(type = SecuritySchemeType.HTTP, name = "bearerAuth", in = SecuritySchemeIn.HEADER, scheme = "bearer")
@Slf4j
public class OpenApiConfig implements CommandLineRunner {
    private final MailJobRepository mailJobRepository;

    @Value("${application.admin.email}")
    private String adminEmail;

    public OpenApiConfig(MailJobRepository mailJobRepository) {
        this.mailJobRepository = mailJobRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        log.warn("swagger is enabled");
        mailJobRepository.sendDelayedMail(List.of(adminEmail), "Artcoded error (swagger enabled on prod)",
                "<p>%s</p>".formatted("Swagger / api doc is enabled. This should only be on dev"), false, List.of(),
                LocalDateTime.now().plusHours(1));

    }

}
