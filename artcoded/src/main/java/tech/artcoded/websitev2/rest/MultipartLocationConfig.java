package tech.artcoded.websitev2.rest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Tomcat rejects every upload when the multipart temp directory doesn't exist (undertow didn't care), so it is created
 * at startup, before the web server accepts requests.
 */
@Configuration(proxyBeanMethods = false)
@Slf4j
public class MultipartLocationConfig implements InitializingBean {
    private final String location;

    public MultipartLocationConfig(@Value("${spring.servlet.multipart.location:}") String location) {
        this.location = location;
    }

    @Override
    public void afterPropertiesSet() {
        createLocation(location);
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
