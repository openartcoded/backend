package tech.artcoded.websitev2.security.oauth;

import tech.artcoded.websitev2.utils.service.MailService;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import lombok.extern.slf4j.Slf4j;

// 2025-10-17 21:56 experiment: swagger
@Configuration
@ConditionalOnProperty(name = "application.openapi.enabled", havingValue = "true")
@OpenAPIDefinition(info = @Info(title = "Artcoded", version = "1.0.0"), security = {
        @SecurityRequirement(name = "bearerAuth") })
@SecurityScheme(type = SecuritySchemeType.HTTP, name = "bearerAuth", in = SecuritySchemeIn.HEADER, scheme = "bearer")
@Slf4j
public class OpenApiConfig implements CommandLineRunner {
    private final MailService mailService;

    @Value("${application.admin.email}")
    private String adminEmail;

    public OpenApiConfig(MailService mailService) {
        this.mailService = mailService;
    }

    @Override
    public void run(String... args) throws Exception {
        log.warn("swagger is enabled");
        mailService.sendMail(List.of(adminEmail), "Artcoded error",
                "<p>%s</p>".formatted("Swagger / api doc is enabled. This should only be on dev"), false, List::of);

    }

}
