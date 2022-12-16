package tech.artcoded.websitev2.pages.personal;


import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;


@RestController
@RequestMapping("/api/personal-info")
public class PersonalInfoController {
  private final PersonalInfoService service;

  public PersonalInfoController(PersonalInfoService service) {
    this.service = service;
  }

  @PostMapping(value = "/submit",
    consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<PersonalInfo> save(
    @RequestParam("ceoFullName") String ceoFullName,
    @RequestParam("note") String note,
    @RequestParam("organizationAddress") String organizationAddress,
    @RequestParam("financeCharge") BigDecimal financeCharge,
    @RequestParam("organizationCity") String organizationCity,
    @RequestParam("organizationName") String organizationName,
    @RequestParam("organizationBankAccount") String organizationBankAccount,
    @RequestParam("organizationBankBIC") String organizationBankBIC,
    @RequestParam("organizationEmailAddress") String organizationEmailAddress,
    @RequestParam("organizationPostCode") String organizationPostCode,
    @RequestParam("maxDaysToPay") Integer maxDaysToPay,
    @RequestParam("organizationPhoneNumber") String organizationPhoneNumber,
    @RequestParam("vatNumber") String vatNumber,
    @RequestPart(value = "signature",
      required = false) MultipartFile signature,
    @RequestPart(value = "logo",
      required = false) MultipartFile logo) {

    return ResponseEntity.ok(service.save(PersonalInfo.builder().ceoFullName(ceoFullName)
      .note(note)
      .organizationAddress(organizationAddress)
      .organizationCity(organizationCity)
      .financeCharge(financeCharge)
      .organizationName(organizationName)
      .organizationBankAccount(organizationBankAccount)
      .organizationBankBIC(organizationBankBIC)
      .organizationEmailAddress(organizationEmailAddress)
      .organizationPostCode(organizationPostCode)
      .organizationPhoneNumber(organizationPhoneNumber)
      .maxDaysToPay(maxDaysToPay)
      .vatNumber(vatNumber)
      .build(), logo, signature));
  }

  @GetMapping
  public PersonalInfo get() {
    return service.get();
  }

}
