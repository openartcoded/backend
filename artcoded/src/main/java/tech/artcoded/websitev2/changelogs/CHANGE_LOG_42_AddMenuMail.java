package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.io.IOException;
import java.util.Date;
import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.pages.settings.menu.MenuLink;
import tech.artcoded.websitev2.pages.settings.menu.MenuLinkRepository;
import tech.artcoded.websitev2.pages.settings.menu.RouterLinkOption;

@Slf4j
@ChangeUnit(id = "add-menu-mail", order = "42", author = "Nordine Bittich")
public class CHANGE_LOG_42_AddMenuMail {

  @RollbackExecution
  public void rollbackExecution() {
  }

  @Execution
  public void execute(MenuLinkRepository menuLinkRepository)
      throws IOException {
    // reset order
    menuLinkRepository.reorder();
    menuLinkRepository.findByOrderByOrderDesc().stream().findFirst().ifPresent(
        m -> {
          log.info("saving mail menu");
          menuLinkRepository.save(
              MenuLink.builder()
                  .description("Mails")
                  .icon(new String[] { "fas", "mail-bulk" })
                  .routerLink(new String[] { "mails" })
                  .title("Mails")
                  .order(m.getOrder() + 1)
                  .updatedDate(new Date())
                  .routerLinkActiveOptions(
                      RouterLinkOption.builder().exact(true).build())
                  .build());
        });
  }
}
