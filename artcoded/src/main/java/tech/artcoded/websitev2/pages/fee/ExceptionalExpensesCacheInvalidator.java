package tech.artcoded.websitev2.pages.fee;

import java.util.Optional;

import org.springframework.cache.CacheManager;
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.AfterDeleteEvent;
import org.springframework.data.mongodb.core.mapping.event.AfterSaveEvent;
import org.springframework.stereotype.Component;

/** The exceptional expenses are computed again when an expense is saved (price, flag...) or deleted. */
@Component
public class ExceptionalExpensesCacheInvalidator extends AbstractMongoEventListener<Fee> {
    private final CacheManager cacheManager;

    public ExceptionalExpensesCacheInvalidator(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    void invalidate() {
        Optional.ofNullable(cacheManager.getCache(FeeService.EXCEPTIONAL_CACHE)).ifPresent(c -> c.clear());
    }

    @Override
    public void onAfterSave(AfterSaveEvent<Fee> event) {
        invalidate();
    }

    @Override
    public void onAfterDelete(AfterDeleteEvent<Fee> event) {
        invalidate();
    }
}
