
package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import java.io.IOException;

@ChangeUnit(id = "add-demo-mode", order = "48", author = "Nordine Bittich")
public class CHANGE_LOG_48_ADD_DEMO_MODE {

  @RollbackExecution
  public void rollbackExecution() {
  }

  @Execution
  public void execute(MongoTemplate mongoTemplate) throws IOException {
    if (mongoTemplate.collectionExists("personalInfo")) {
      mongoTemplate.getCollection("personalInfo").updateMany(Filters.empty(),
          Updates.set("demoMode", false));
    }

  }

}
