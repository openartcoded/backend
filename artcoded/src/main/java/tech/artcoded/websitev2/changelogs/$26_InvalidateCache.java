package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.io.IOException;

import static java.util.Optional.ofNullable;

@Slf4j
@ChangeUnit(id = "invalidate-cache",
  order = "26",
  author = "Nordine Bittich")
public class $26_InvalidateCache {

  @RollbackExecution
  public void rollbackExecution() {
  }

  @Execution
  public void execute(CacheManager cacheManager) throws IOException {
    cacheManager.getCacheNames()
      .forEach(c -> ofNullable(c).filter(StringUtils::isNotEmpty)
        .map(cacheManager::getCache)
        .ifPresent(Cache::clear));
  }


}
