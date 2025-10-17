package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import tech.artcoded.websitev2.pages.settings.menu.MenuLinkRepository;

import java.io.IOException;

@ChangeUnit(id = "remove-privacy", order = "14", author = "Nordine Bittich")
public class CHANGE_LOG_14_RemovePrivacy {

    @RollbackExecution
    public void rollbackExecution() {
    }

    @Execution
    public void execute(MenuLinkRepository menuLinkRepository) throws IOException {
        menuLinkRepository.findAll().stream().filter(m -> "Privacy".equalsIgnoreCase(m.getTitle())).findFirst()
                .ifPresent(menuLinkRepository::delete);

    }

}
