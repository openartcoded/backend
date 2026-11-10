package tech.artcoded.websitev2.pages.dossier.report;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

/** The cached reports expire after application.reports.cache-ttl (7 days by default), see {@link ReportCache}. */
@Configuration(proxyBeanMethods = false)
public class ReportCacheConfig {
    @Bean
    public RedisCacheManagerBuilderCustomizer reportCacheCustomizer(
            @Value("${application.reports.cache-ttl:7d}") Duration ttl) {
        return builder -> builder.withCacheConfiguration(ReportCache.CACHE,
                RedisCacheConfiguration.defaultCacheConfig().entryTtl(ttl));
    }
}
