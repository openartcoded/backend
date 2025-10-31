package tech.artcoded.websitev2.upload.api;

import javax.inject.Inject;

import org.openapitools.client.ApiClient;
import org.openapitools.client.Configuration;
import org.openapitools.client.api.TemplateRoutesApi;
import org.openapitools.client.api.UploadRoutesApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;

import lombok.extern.slf4j.Slf4j;

@org.springframework.context.annotation.Configuration
@Slf4j
public class ApiConfig {

  @Bean
  @Scope("prototype")
  public ApiClient getClient(@Value("${vendors.file-service.basePath}") String basePath) {
    ApiClient defaultClient = Configuration.getDefaultApiClient();
    defaultClient.setBasePath(basePath);
    log.debug("base path used: {}", basePath);
    return defaultClient;
  }

  @Bean
  @Inject
  public UploadRoutesApi uploadRoutesApi(ApiClient apiClient) {
    return new UploadRoutesApi(apiClient);
  }

  @Bean
  @Inject
  public TemplateRoutesApi templateRoutesApi(ApiClient apiClient) {
    return new TemplateRoutesApi(apiClient);
  }
}
