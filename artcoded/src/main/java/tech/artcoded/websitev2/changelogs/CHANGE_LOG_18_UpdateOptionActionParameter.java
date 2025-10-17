package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.io.IOException;
import java.util.Map;

@ChangeUnit(id = "update-option-action-parameter", order = "18", author = "Nordine Bittich")
public class CHANGE_LOG_18_UpdateOptionActionParameter {

    @RollbackExecution
    public void rollbackExecution() {
    }

    @Execution
    public void execute(MongoTemplate mongoTemplate) throws IOException {
        if (mongoTemplate.collectionExists("reminderTask")) {
            Query all = Query.query(Criteria.where("actionParameters").exists(true));
            mongoTemplate.updateMulti(all, Update.update("actionParameters.$[].options", Map.of()), "reminderTask");
        }

    }

}
