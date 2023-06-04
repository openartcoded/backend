package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.io.IOException;
import java.util.Optional;

import javax.cache.Cache;
import javax.cache.CacheManager;

@ChangeUnit(id = "clear-cache-invoice-summary", order = "39", author = "Nordine Bittich")
public class $39_ClearCacheInvSummary {

  @RollbackExecution
  public void rollbackExecution() {
  }

  @Execution
  public void execute(CacheManager cacheManager) throws IOException {
    Optional.ofNullable(cacheManager.getCache("invoiceSummary"))
        .ifPresent(Cache::clear);
  }

}
