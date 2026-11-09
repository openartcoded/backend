package tech.artcoded.websitev2.rest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Tomcat rejects every upload when the multipart temp directory doesn't exist (undertow didn't care), so it is
 * created before the server starts.
 */
@Configuration(proxyBeanMethods = false)
@Slf4j
public class MultipartLocationConfig {

    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> multipartLocationCreator(
            MultipartProperties multipartProperties) {
        return _ -> createLocation(multipartProperties.getLocation());
    }

    static void createLocation(String location) {
        if (!StringUtils.hasText(location)) {
            return;
        }
        try {
            Files.createDirectories(Path.of(location));
        } catch (IOException e) {
            log.warn("could not create the multipart location {}: {}", location, e.getMessage());
        }
    }
}
