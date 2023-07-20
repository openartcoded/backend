package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.io.IOException;
import java.util.Optional;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

@ChangeUnit(id = "clear-cache-invoice-summary", order = "39", author = "Nordine Bittich")
public class CHANGE_LOG_39_ClearCacheInvSummary {

  @RollbackExecution
  public void rollbackExecution() {
  }

  @Execution
  public void execute(CacheManager cacheManager) throws IOException {
    Optional.ofNullable(cacheManager.getCache("invoiceSummary"))
        .ifPresent(Cache::clear);
  }

}
