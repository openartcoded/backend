
package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.pages.report.PostRepository;
import tech.artcoded.websitev2.pages.report.Post.PostStatus;

import java.io.IOException;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@ChangeUnit(id = "set-status-for-posts", order = "57", author = "Nordine Bittich")
@Slf4j
public class CHANGE_LOG_57_SetStatusForPost {

  @RollbackExecution
  public void rollbackExecution() {
  }

  @Execution
  public void execute(PostRepository repository, MongoTemplate template) throws IOException {

    repository.saveAll(repository.findAll()
        .stream()
        .filter(p -> p.getStatus() == null)
        .map(p -> p.toBuilder().status(PostStatus.IN_PROGRESS).build())
        .toList());
    if (template.collectionExists("post")) {
      log.info("removing old draft field..");
      Query query = new Query(); // or your own criteria
      Update update = new Update().unset("draft");
      template.updateMulti(query, update, "post");
    }

  }

}
