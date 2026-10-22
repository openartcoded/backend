package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import tech.artcoded.websitev2.pages.client.BillableClient;
import tech.artcoded.websitev2.pages.client.BillableClientRepository;

@ChangeUnit(id = "add-rates-to-billable-client", order = "62", author = "openartcoded")
public class CHANGE_LOG_62_AddRatesToBillableClient {

    @RollbackExecution
    public void rollbackExecution() {
    }

    @Execution
    public void execute(BillableClientRepository repo) {
        // each client gets its legacy rate/rateType as its first (default) rate
        for (var client : repo.findAll()) {
            var rates = BillableClient.normalizeRates(client.getRates(), client.getRate(), client.getRateType());
            repo.save(client.toBuilder().rates(rates).build());
        }
    }
}
