package tech.artcoded.websitev2.template;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

/**
 * Templates rarely change: their caches live long (application.templates.cache-ttl, 90 days by default) and are evicted
 * whenever a template is saved, made the default or deleted (see {@link OaTemplateService}). They can also be cleared
 * like any other cache (/api/cache/clear).
 */
@Configuration(proxyBeanMethods = false)
public class TemplateCacheConfig {
    /** the default template of each type */
    public static final String DEFAULTS = "oa_template_defaults";
    /** the source of the v1 templates (downloaded from the file service) */
    public static final String SOURCES = "oa_template_sources";

    @Bean
    public RedisCacheManagerBuilderCustomizer templateCachesCustomizer(
            @Value("${application.templates.cache-ttl:90d}") Duration ttl) {
        var config = RedisCacheConfiguration.defaultCacheConfig().entryTtl(ttl);
        return builder -> builder.withCacheConfiguration(DEFAULTS, config).withCacheConfiguration(SOURCES, config);
    }
}
