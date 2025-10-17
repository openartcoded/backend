package tech.artcoded.websitev2.upload;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class CorrelationLinkRefreshScheduler {

    private static final AtomicBoolean IS_INITIALIZED = new AtomicBoolean(false);

    private final CorrelationLinkService linkService;

    private final CacheManager cacheManager;

    public CorrelationLinkRefreshScheduler(CorrelationLinkService linkService, CacheManager cacheManager) {
        this.linkService = linkService;
        this.cacheManager = cacheManager;
    }

    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.HOURS, initialDelay = 0)
    @CacheEvict(cacheNames = CorrelationLinkService.CACHE_LINKS_KEY, allEntries = true, beforeInvocation = true)
    @SneakyThrows
    public void scheduleRefresh() {
        log.info("warmup correlationLinks cache...");
        clearCorrelationLinksCaches();
        var links = this.linkService.getLinks();
        if (!IS_INITIALIZED.get()) {
            IS_INITIALIZED.set(true);
            log.info("Correlation Links Content:\n{}", new ObjectMapper().writeValueAsString(links));
        }
    }

    private void clearCorrelationLinksCaches() {
        cacheManager.getCacheNames().stream().filter(name -> name.endsWith("correlation_links")).forEach(name -> {
            log.info("clearing cache {}", name);
            var cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        });
    }
}
