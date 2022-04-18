package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import tech.artcoded.websitev2.pages.settings.menu.MenuLinkRepository;
import tech.artcoded.websitev2.pages.task.ReminderTask;
import tech.artcoded.websitev2.pages.task.ReminderTaskService;

import java.io.IOException;


@Slf4j
@ChangeUnit(id = "remove-immo-filter",
  order = "12",
  author = "Nordine Bittich")
public class $12_RemoveImmoFilter {

  @RollbackExecution
  public void rollbackExecution() {
  }

  @Execution
  public void execute(MongoTemplate mongoTemplate,
                      MenuLinkRepository menuLinkRepository,
                      ReminderTaskService taskService) throws IOException {
    taskService.findByActionKeyNotNull().stream().filter(t -> "IMMO_FILTER_ACTION".equals(t.getActionKey()))
      .map(ReminderTask::getId)
      .forEach(taskService::delete);

    menuLinkRepository.findAll().stream().filter(m -> "Immo".equalsIgnoreCase(m.getTitle()))
      .findFirst()
      .ifPresent(menuLinkRepository::delete);

    if (mongoTemplate.collectionExists("immoFilter")) {
      mongoTemplate.dropCollection("immoFilter");
    }
  }

}
