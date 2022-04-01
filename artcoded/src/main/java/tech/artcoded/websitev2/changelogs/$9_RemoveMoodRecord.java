package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.io.IOException;

@Slf4j
@ChangeUnit(id = "remove-mood-record",
            order = "9",
            author = "Nordine Bittich")
public class $9_RemoveMoodRecord {

  @RollbackExecution
  public void rollbackExecution(MongoTemplate mongoTemplate) {
  }

  @Execution
  public void execute(MongoTemplate mongoTemplate) throws IOException {
    if (mongoTemplate.collectionExists("moodRecord")) {
      mongoTemplate.dropCollection("moodRecord");
    }

  }
}
