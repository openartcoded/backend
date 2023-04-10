package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import tech.artcoded.websitev2.pages.client.BillableClientRepository;
import tech.artcoded.websitev2.pages.personal.PersonalInfoRepository;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@ChangeUnit(id = "extra-fields-billable-client", order = "32", author = "Nordine Bittich")
public class $32_ExtraFieldsBillableClient {

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
