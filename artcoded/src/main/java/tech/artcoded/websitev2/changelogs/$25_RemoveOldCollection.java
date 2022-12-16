package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.io.IOException;

@ChangeUnit(id = "remove-old-collections", order = "25", author = "Nordine Bittich")
public class $25_RemoveOldCollection {

  @RollbackExecution
  public void rollbackExecution() {
  }

  @Execution
  public void execute(MongoTemplate mongoTemplate) throws IOException {
    if (mongoTemplate.collectionExists("immoCachedSearch")) {
      mongoTemplate.dropCollection("immoCachedSearch");
    }
    if (mongoTemplate.collectionExists("user")) {
      mongoTemplate.dropCollection("user");
    }
    if (mongoTemplate.collectionExists("news")) {
      mongoTemplate.dropCollection("news");
    }
  }

}
