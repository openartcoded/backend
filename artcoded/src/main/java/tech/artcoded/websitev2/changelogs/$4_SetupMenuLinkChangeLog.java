package tech.artcoded.websitev2.changelogs;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import tech.artcoded.websitev2.api.helper.IdGenerators;
import tech.artcoded.websitev2.pages.settings.menu.MenuLink;
import tech.artcoded.websitev2.pages.settings.menu.MenuLinkRepository;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;

@Slf4j
@ChangeUnit(id = "setup-menu-link",
            order = "4",
            author = "Nordine Bittich")
public class $4_SetupMenuLinkChangeLog {

  @RollbackExecution
  public void rollbackExecution(MenuLinkRepository repository) {
  }

  @Execution
  public void execute(MenuLinkRepository repository) throws IOException {
    if (repository.count() == 0) {
      var mapper = new ObjectMapper();
      var menuLinks = new ClassPathResource("settings/menu-link.json");

      log.info("setup menu links...");
      AtomicInteger counter = new AtomicInteger(0);
      Stream.of(mapper.readValue(menuLinks.getInputStream(), MenuLink[].class))
            .map(link -> link.toBuilder().id(IdGenerators.get()).order(counter.getAndIncrement()).build())
            .map(menuLink -> ofNullable(menuLink.getId()).flatMap(repository::findById)
                                                         .map(MenuLink::toBuilder)
                                                         .orElseGet(menuLink::toBuilder)
                                                         .routerLink(menuLink.getRouterLink())
                                                         .updatedDate(new Date())
                                                         .routerLinkActiveOptions(menuLink.getRouterLinkActiveOptions())
                                                         .icon(menuLink.getIcon())
                                                         .order(menuLink.getOrder())
                                                         .show(menuLink.isShow())
                                                         .description(menuLink.getDescription())
                                                         .title(menuLink.getTitle())
                                                         .build())
            .forEach(repository::save);
    }
  }
}
