package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import tech.artcoded.websitev2.pages.personal.PersonalInfoRepository;

import java.io.IOException;
import java.util.Date;
import java.util.List;

@ChangeUnit(id = "accountants", order = "31", author = "Nordine Bittich")
public class CHANGE_LOG_31_Accountants {

    @RollbackExecution
    public void rollbackExecution() {
    }

    @Execution
    public void execute(PersonalInfoRepository repo) throws IOException {
        for (var pi : repo.findAll()) {
            if (pi.getAccountants() == null) {
                repo.save(pi.toBuilder().updatedDate(new Date()).accountants(List.of()).build());
            }
        }
    }

}
