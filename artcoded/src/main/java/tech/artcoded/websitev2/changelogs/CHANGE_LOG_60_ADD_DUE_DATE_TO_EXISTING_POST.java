
package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.pages.report.PostRepository;
import tech.artcoded.websitev2.utils.helper.DateHelper;

import java.io.IOException;

@ChangeUnit(id = "add-due-date-to-existing-post", order = "60", author = "Nordine Bittich")
@Slf4j
public class CHANGE_LOG_60_ADD_DUE_DATE_TO_EXISTING_POST {

  @RollbackExecution
  public void rollbackExecution() {
  }

  @Execution
  public void execute(PostRepository postRepository) throws IOException {

    postRepository.findAll().stream().filter(p -> p.getDueDate() == null)
        .map(p -> p.toBuilder()
            .dueDate(DateHelper.toDate(DateHelper.toLocalDateTime(p.getCreationDate()).plusWeeks(3))).build())
        .forEach(postRepository::save);

  }

}
