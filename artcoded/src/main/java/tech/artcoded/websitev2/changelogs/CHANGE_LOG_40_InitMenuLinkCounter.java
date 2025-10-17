package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import tech.artcoded.websitev2.pages.settings.menu.MenuLinkRepository;

import java.io.IOException;

@ChangeUnit(id = "init-menu-link-counter", order = "40", author = "Nordine Bittich")
public class CHANGE_LOG_40_InitMenuLinkCounter {

    @RollbackExecution
    public void rollbackExecution() {
    }

    @Execution
    public void execute(MenuLinkRepository repo) throws IOException {
        repo.findAll().stream().filter(ml -> ml.getNumberOfTimesClicked() == null)
                .forEach(ml -> repo.save(ml.toBuilder().numberOfTimesClicked(0L).build()));

    }

}
