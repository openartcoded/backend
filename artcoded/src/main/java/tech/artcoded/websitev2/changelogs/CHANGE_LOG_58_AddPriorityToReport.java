
package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.pages.report.PostRepository;
import tech.artcoded.websitev2.pages.report.Post;

import java.io.IOException;

import org.springframework.data.mongodb.core.MongoTemplate;

@ChangeUnit(id = "add-priority-to-report", order = "58", author = "Nordine Bittich")
@Slf4j
public class CHANGE_LOG_58_AddPriorityToReport {

  @RollbackExecution
  public void rollbackExecution() {
  }

  @Execution
  public void execute(PostRepository repository, MongoTemplate template) throws IOException {

    repository.findAll().stream().map(f -> f.toBuilder()
        .priority(Post.Priority.MEDIUM)
        .bookmarked(false).build()).forEach(repository::save);

  }

}
