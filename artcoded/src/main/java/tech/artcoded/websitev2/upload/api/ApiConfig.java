package tech.artcoded.websitev2.upload.api;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import javax.inject.Inject;

import org.openapitools.client.ApiClient;
import org.openapitools.client.Configuration;
import org.openapitools.client.JSON;
import org.openapitools.client.api.TemplateRoutesApi;
import org.openapitools.client.api.UploadRoutesApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;

import com.google.gson.JsonDeserializer;
import com.google.gson.JsonParseException;

import lombok.extern.slf4j.Slf4j;

@org.springframework.context.annotation.Configuration
@Slf4j
public class ApiConfig {

    @Bean
    @Scope("prototype")
    public ApiClient getClient(@Value("${vendors.file-service.basePath}") String basePath,
            @Value("${application.tmpfs}") String tmpfsPath) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath(basePath);
        defaultClient.setTempFolderPath(tmpfsPath);
        // fixme maybe should be configurable
        defaultClient.setConnectTimeout(30_000); // 60 secs
        defaultClient.setReadTimeout(600_000); // 10 minutes
        defaultClient.setWriteTimeout(600_000); // 10 minutes
        var gsonBuilder = JSON.createGson();
        gsonBuilder.registerTypeAdapter(OffsetDateTime.class, (JsonDeserializer<OffsetDateTime>) (json, _, _) -> {
            if (json.isJsonObject() && json.getAsJsonObject().has("$date")) {
                long millis = json.getAsJsonObject().get("$date").getAsJsonObject().get("$numberLong").getAsLong();
                return Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC);
            }
            throw new JsonParseException("Unexpected date format: " + json);
        });
        var gson = gsonBuilder.create();

        JSON.setGson(gson);

        log.debug("base path used: {}", basePath);
        return defaultClient;
    }

    @Bean
    @Inject
    public UploadRoutesApi uploadRoutesApi(ApiClient apiClient) {
        var api = new UploadRoutesApi(apiClient);

        return api;
    }

    @Bean
    @Inject
    public TemplateRoutesApi templateRoutesApi(ApiClient apiClient) {
        return new TemplateRoutesApi(apiClient);
    }
}
