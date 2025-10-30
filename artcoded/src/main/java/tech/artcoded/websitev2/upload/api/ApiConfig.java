package tech.artcoded.websitev2.upload.api;

import org.openapitools.client.ApiClient;
import org.openapitools.client.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import lombok.extern.slf4j.Slf4j;

@org.springframework.context.annotation.Configuration
@Slf4j
public class ApiConfig {

    @Bean
    public ApiClient getClient(@Value("${vendors.file-service.basePath}") String basePath) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath(basePath);
        log.debug("base path used: {}", basePath);
        return defaultClient;
    }
}
