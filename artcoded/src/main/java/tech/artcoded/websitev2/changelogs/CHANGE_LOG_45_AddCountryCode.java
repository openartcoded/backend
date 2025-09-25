package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.pages.client.BillableClient;
import tech.artcoded.websitev2.pages.client.BillableClientRepository;
import tech.artcoded.websitev2.pages.cv.repository.CurriculumRepository;
import tech.artcoded.websitev2.pages.cv.service.CurriculumService;
import tech.artcoded.websitev2.pages.personal.PersonalInfoRepository;
import tech.artcoded.websitev2.pages.personal.PersonalInfoService;
import tech.artcoded.websitev2.utils.helper.IdGenerators;

@Slf4j
@ChangeUnit(id = "add-country-code", order = "45", author = "Nordine Bittich")
public class CHANGE_LOG_45_AddCountryCode {

  @RollbackExecution
  public void rollbackExecution() {
  }

  @Execution
  public void execute(BillableClientRepository bcr, PersonalInfoService pis, PersonalInfoRepository pir)
      throws IOException {
    var newBillableClients = bcr.findAll()
    .stream()
    .map(bc-> bc.toBuilder().countryCode("BE").build())
    .toList();
    bcr.saveAll(newBillableClients);
    var newPersonalInfo = pis.get().toBuilder().countryCode("BE").build();
    pir.save(newPersonalInfo);
  }
}
