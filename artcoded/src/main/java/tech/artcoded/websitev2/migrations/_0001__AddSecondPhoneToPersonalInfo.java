package tech.artcoded.websitev2.migrations;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Rollback;
import io.flamingock.api.annotations.TargetSystem;
import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.mongodb.MigrationTargetSystem;
import tech.artcoded.websitev2.pages.personal.PersonalInfo;

/**
 * First Flamingock change (the previous ones are the legacy Mongock change units in the changelogs package). Adds the
 * second phone number to the personal info, empty by default, like the first one is stored.
 * <p>
 * Flamingock conventions: the order is in the class name ({@code _NNNN__}), the class lives in the stage package
 * declared by {@code @EnableFlamingock} and a change must never be modified once it ran: add a new one instead.
 */
@Slf4j
@TargetSystem(id = MigrationTargetSystem.ID)
@Change(id = "add-second-phone-to-personal-info", author = "Nordine Bittich")
public class _0001__AddSecondPhoneToPersonalInfo {
    static final String FIELD = "organizationSecondPhoneNumber";

    @Apply
    public void apply(MongoTemplate mongoTemplate) {
        // only documents without the field: never overwrite a number already set
        var result = mongoTemplate.updateMulti(Query.query(Criteria.where(FIELD).exists(false)),
                Update.update(FIELD, ""), PersonalInfo.class);
        log.info("{} personal info document(s) received an empty {}", result.getModifiedCount(), FIELD);
    }

    @Rollback
    public void rollback(MongoTemplate mongoTemplate) {
        // only what this change added: a number entered since then is kept
        mongoTemplate.updateMulti(Query.query(Criteria.where(FIELD).is("")), new Update().unset(FIELD),
                PersonalInfo.class);
    }
}
