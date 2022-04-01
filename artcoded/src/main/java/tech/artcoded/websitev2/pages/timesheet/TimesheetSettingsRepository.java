package tech.artcoded.websitev2.pages.timesheet;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface TimesheetSettingsRepository extends MongoRepository<TimesheetSettings, String> {
}
