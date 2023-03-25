package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import tech.artcoded.websitev2.pages.personal.PersonalInfo;
import tech.artcoded.websitev2.pages.personal.PersonalInfoRepository;
import tech.artcoded.websitev2.pages.personal.PersonalInfoService;
import tech.artcoded.websitev2.rest.util.MockMultipartFile;

import java.math.BigDecimal;

import static org.apache.commons.io.IOUtils.toByteArray;
import static tech.artcoded.websitev2.utils.func.CheckedSupplier.toSupplier;

@Slf4j
@ChangeUnit(id = "init-personal-info", order = "5", author = "Nordine Bittich")
public class $5_InitPersonalInfoChangeLog {

  @RollbackExecution
  public void rollbackExecution(PersonalInfoRepository repository) {
  }

  @Execution
  public void execute(PersonalInfoService personalInfoService, PersonalInfoRepository repository) {

    var defaultLogo = new ClassPathResource("misc/logo.png");
    var defaultSignature = new ClassPathResource("misc/signature.png");
    repository.findAll()
        .stream()
        .findFirst()
        .ifPresentOrElse(personalInfo -> log.info("Current personal info {}", personalInfo), () -> {
          personalInfoService.save(PersonalInfo.builder().ceoFullName("Bittich Nordine")
              .note(
                  "la facturation faite au client en vertu du contrat de consultance inclut une cession des droits d&#39;auteurs (droits patrimoniaux) sur &oelig;uvres, codes et programmes effectu&eacute;s par notre soci&eacute;t&eacute;, &agrave; charge pour notre entreprise de r&eacute;mun&eacute;rer ses auteurs.")
              .organizationAddress("Bekkerstreet 83")
              .organizationCity("London")
              .organizationName("MYCOMPANY SPRL")
              .organizationBankAccount("BE88 0099 4444 7777")
              .organizationEmailAddress("contact@woops.com")
              .organizationPostCode("1000")
              .maxDaysToPay(30)
              .organizationPhoneNumber("0485.55.55.55")
              .vatNumber("BE 0555.555.555")
              .organizationBankBIC("GEBABEBX")
              .financeCharge(new BigDecimal("1.5"))
              .build(),
              MockMultipartFile.builder()
                  .contentType(MediaType.IMAGE_PNG_VALUE)
                  .name("default-logo.png")
                  .bytes(toSupplier(() -> toByteArray(defaultLogo.getInputStream())).get())
                  .originalFilename("default-logo.png")
                  .build(),
              MockMultipartFile.builder()
                  .contentType(MediaType.IMAGE_PNG_VALUE)
                  .name("default-signature.png")
                  .bytes(toSupplier(() -> toByteArray(defaultSignature.getInputStream())).get())
                  .originalFilename("default-signature.png")
                  .build());
        });
  }
}
