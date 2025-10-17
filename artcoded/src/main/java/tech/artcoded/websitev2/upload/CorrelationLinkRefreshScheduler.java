package tech.artcoded.websitev2.upload;

import org.springframework.boot.CommandLineRunner;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CorrelationLinkRefreshScheduler implements CommandLineRunner {

  private final CorrelationLinkService linkService;

  public CorrelationLinkRefreshScheduler(CorrelationLinkService linkService) {
    this.linkService = linkService;
  }

  @Override
  @CacheEvict(cacheNames = CorrelationLinkService.CACHE_LINKS_KEY, allEntries = true, beforeInvocation = true)
  public void run(String... args) throws Exception {
    log.info("warmup correlationLinks cache...");
    log.info("Correlation Links Content:\n{}", new ObjectMapper().writeValueAsString(this.linkService.getLinks()));
  }

  @Scheduled(fixedDelay = 30_000, initialDelay = 120_000)
  @CacheEvict(cacheNames = CorrelationLinkService.CACHE_LINKS_KEY, allEntries = true, beforeInvocation = true)
  public void scheduleRefresh() {
    log.info("refreshing correlation links cache...");
    this.linkService.getLinks();
  }
}
