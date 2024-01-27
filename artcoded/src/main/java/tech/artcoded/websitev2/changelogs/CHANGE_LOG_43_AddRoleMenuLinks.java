package tech.artcoded.websitev2.changelogs;

import static tech.artcoded.websitev2.security.oauth.Role.ADMIN;
import static tech.artcoded.websitev2.security.oauth.Role.REGULATOR_OR_ACCOUNTANT;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.pages.settings.menu.MenuLink;
import tech.artcoded.websitev2.pages.settings.menu.MenuLinkRepository;
import tech.artcoded.websitev2.pages.settings.menu.RouterLinkOption;

@Slf4j
@ChangeUnit(id = "add-role-menu-links", order = "43", author = "Nordine Bittich")
public class CHANGE_LOG_43_AddRoleMenuLinks {

  @RollbackExecution
  public void rollbackExecution() {
  }

  @Execution
  public void execute(MenuLinkRepository menuLinkRepository)
      throws IOException {
    menuLinkRepository.findByOrderByOrderDesc().stream().forEach(m -> {
      var roles = Arrays.asList(ADMIN.getAuthority());

      if (List.of("Home", "Expenses", "Invoices", "Dossiers", "Documents",
          "Uploads", "Clients")
          .contains(m.getTitle())) {
        roles.add(REGULATOR_OR_ACCOUNTANT.getAuthority());
      }

      menuLinkRepository.save(
          MenuLink.builder()
              .description("Mails")
              .icon(new String[] { "fas", "mail-bulk" })
              .routerLink(new String[] { "mails" })
              .title("Mails")
              .order(m.getOrder() + 1)
              .roles(roles)
              .updatedDate(new Date())
              .routerLinkActiveOptions(
                  RouterLinkOption.builder().exact(true).build())
              .build());
    });
  }
}
