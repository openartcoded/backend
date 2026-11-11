package tech.artcoded.websitev2.pages.timesheet.stats;

import java.util.Optional;

import org.springframework.cache.CacheManager;
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.AfterDeleteEvent;
import org.springframework.data.mongodb.core.mapping.event.AfterSaveEvent;
import org.springframework.stereotype.Component;

import tech.artcoded.websitev2.pages.timesheet.Timesheet;

/** The stats are computed again when a timesheet is saved (a day, a rate, closed...) or deleted. */
@Component
public class TimesheetStatsCacheInvalidator extends AbstractMongoEventListener<Timesheet> {
    private final CacheManager cacheManager;

    public TimesheetStatsCacheInvalidator(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    void invalidate() {
        Optional.ofNullable(cacheManager.getCache(TimesheetStatsService.CACHE)).ifPresent(c -> c.clear());
    }

    @Override
    public void onAfterSave(AfterSaveEvent<Timesheet> event) {
        invalidate();
    }

    @Override
    public void onAfterDelete(AfterDeleteEvent<Timesheet> event) {
        invalidate();
    }
}
