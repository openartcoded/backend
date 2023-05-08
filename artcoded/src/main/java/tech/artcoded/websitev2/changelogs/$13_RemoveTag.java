package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.data.mongodb.core.MongoTemplate;
import tech.artcoded.websitev2.pages.fee.FeeRepository;
import tech.artcoded.websitev2.pages.fee.Label;
import tech.artcoded.websitev2.pages.fee.LabelService;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@ChangeUnit(id = "remove-tag", order = "13", author = "Nordine Bittich")
public class $13_RemoveTag {

  @RollbackExecution
  public void rollbackExecution() {
  }

  @Execution
  public void execute(MongoTemplate mongoTemplate,
      FeeRepository feeRepository,
      LabelService labelService) throws IOException {
    if (mongoTemplate.collectionExists("defaultPriceForTag")) {
      mongoTemplate.dropCollection("defaultPriceForTag");
    }
    feeRepository.findAll().stream().filter(fee -> "OIL".equalsIgnoreCase(fee.getTag()))
        .map(fee -> fee.toBuilder().tag("GAS").updatedDate(new Date()).build())
        .forEach(feeRepository::save);
    if (labelService.findAll().isEmpty()) {
      Label internet = Label.builder().name("INTERNET")
          .priceHVAT(new BigDecimal("45.5372"))
          .vat(new BigDecimal("9.5628"))
          .colorHex("#007bff").build();
      Label gsm = Label.builder().name("GSM")
          .priceHVAT(new BigDecimal("29.75"))
          .vat(new BigDecimal("6.25"))
          .colorHex("#17a2b8").build();
      Label gas = Label.builder().name("GAS")
          .priceHVAT(BigDecimal.ZERO)
          .vat(BigDecimal.ZERO)
          .colorHex("#343a40").build();
      Label accounting = Label.builder().name("ACCOUNTING")
          .priceHVAT(new BigDecimal("200"))
          .vat(new BigDecimal(42))
          .colorHex("#28a745").build();
      Label leasing = Label.builder().name("LEASING")
          .priceHVAT(new BigDecimal("596.81"))
          .vat(new BigDecimal("125.33"))
          .colorHex("#dc3545").build();
      Label other = Label.builder().name("OTHER")
          .priceHVAT(BigDecimal.ZERO)
          .vat(BigDecimal.ZERO)
          .noDefaultPrice(true)
          .colorHex("#6c757d").build();

      labelService.saveAll(List.of(internet, gsm, gas, accounting, leasing, other));
    }
  }

}
