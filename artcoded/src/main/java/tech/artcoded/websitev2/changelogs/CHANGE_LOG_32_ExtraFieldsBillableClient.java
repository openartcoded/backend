package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import tech.artcoded.websitev2.pages.client.BillableClientRepository;

import java.io.IOException;
import java.math.BigDecimal;

@ChangeUnit(id = "extra-fields-billable-client", order = "32", author = "Nordine Bittich")
public class CHANGE_LOG_32_ExtraFieldsBillableClient {

    @RollbackExecution
    public void rollbackExecution() {
    }

    @Execution
    public void execute(BillableClientRepository repo) throws IOException {
        for (var pi : repo.findAll()) {
            repo.save(pi.toBuilder().taxRate(new BigDecimal(21)).nature("Consulting Work").build());
        }
    }

}
