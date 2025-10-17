package tech.artcoded.websitev2.upload;

import org.springframework.boot.CommandLineRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class CorrelationLinkRefreshScheduler implements CommandLineRunner {

  private final CorrelationLinkService linkService;

  private final CacheManager cacheManager;

  public CorrelationLinkRefreshScheduler(CorrelationLinkService linkService, CacheManager cacheManager) {
    this.linkService = linkService;
    this.cacheManager = cacheManager;
  }

  @Override
  @CacheEvict(cacheNames = CorrelationLinkService.CACHE_LINKS_KEY, allEntries = true, beforeInvocation = true)
  public void run(String... args) throws Exception {
    log.info("warmup correlationLinks cache...");
    clearCorrelationLinksCaches();
    log.info("Correlation Links Content:\n{}", new ObjectMapper().writeValueAsString(this.linkService.getLinks()));
  }

  @Scheduled(fixedDelay = 30_000, initialDelay = 120_000)
  @CacheEvict(cacheNames = CorrelationLinkService.CACHE_LINKS_KEY, allEntries = true, beforeInvocation = true)
  public void scheduleRefresh() {
    clearCorrelationLinksCaches();
    log.info("refreshing correlation links cache...");
    this.linkService.getLinks();
  }

  private void clearCorrelationLinksCaches() {
    cacheManager.getCacheNames().stream()
        .filter(name -> name.endsWith("correlation_links"))
        .forEach(name -> {
          var cache = cacheManager.getCache(name);
          if (cache != null) {
            cache.clear();
          }
        });
  }
}
