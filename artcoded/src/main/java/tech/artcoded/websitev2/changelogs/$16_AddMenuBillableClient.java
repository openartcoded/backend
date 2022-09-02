package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import tech.artcoded.websitev2.pages.fee.FeeRepository;
import tech.artcoded.websitev2.pages.fee.Label;
import tech.artcoded.websitev2.pages.fee.LabelService;
import tech.artcoded.websitev2.pages.settings.menu.MenuLink;
import tech.artcoded.websitev2.pages.settings.menu.MenuLinkRepository;
import tech.artcoded.websitev2.pages.settings.menu.RouterLinkOption;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Date;
import java.util.stream.Stream;


@Slf4j
@ChangeUnit(id = "add-menu-billable-client",
  order = "16",
  author = "Nordine Bittich")
public class $16_AddMenuBillableClient {

  @RollbackExecution
  public void rollbackExecution() {
  }

  @Execution
  public void execute(MenuLinkRepository menuLinkRepository) throws IOException {
    menuLinkRepository.findByOrderByOrderDesc().stream().findFirst().ifPresent(m-> {
      log.info("saving billable clients menu");
      menuLinkRepository.save(MenuLink.builder()
        .description("Billable clients")
          .icon(new String[] {"fas", "user"})
          .routerLink(new String[] {"billable-clients"})
          .title("Clients")
          .order(m.getOrder()+1)
          .updatedDate(new Date())
          .routerLinkActiveOptions(RouterLinkOption.builder().exact(true).build())
        .build());
    });

  }

}
