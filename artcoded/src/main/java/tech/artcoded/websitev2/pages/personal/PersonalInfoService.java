package tech.artcoded.websitev2.pages.personal;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tech.artcoded.websitev2.notification.NotificationService;
import tech.artcoded.websitev2.upload.FileUploadService;

import java.util.Date;
import java.util.Optional;

@Service
@Slf4j
public class PersonalInfoService {
  private final PersonalInfoRepository repository;
  private final NotificationService notificationService;
  private final FileUploadService fileUploadService;
  private static final String PERSONAL_INFO_UPDATED = "PERSONAL_INFO_UPDATED";


  public PersonalInfoService(PersonalInfoRepository repository,
                             NotificationService notificationService, FileUploadService fileUploadService) {
    this.repository = repository;
    this.notificationService = notificationService;
    this.fileUploadService = fileUploadService;
  }

  public PersonalInfo save(PersonalInfo personalInfo, MultipartFile logo, MultipartFile signature) {

    PersonalInfo currentPersonalInfo = get();

    String currentLogoId = currentPersonalInfo.getLogoUploadId();

    if (logo!=null && !logo.isEmpty()) {
      currentLogoId = this.fileUploadService.upload(logo, currentPersonalInfo.getId(), false);
    }

    if (StringUtils.isNotEmpty(currentLogoId) && !currentLogoId.equals(currentPersonalInfo.getLogoUploadId())) {
      fileUploadService.delete(currentPersonalInfo.getLogoUploadId());
    }

    String currentSignatureId = currentPersonalInfo.getSignatureUploadId();

    if (signature!=null && !signature.isEmpty()) {
      currentSignatureId = this.fileUploadService.upload(signature, currentPersonalInfo.getId(), false);
    }

    if (StringUtils.isNotEmpty(currentSignatureId) && !currentSignatureId.equals(currentPersonalInfo.getSignatureUploadId())) {
      fileUploadService.delete(currentPersonalInfo.getSignatureUploadId());
    }

    PersonalInfo updated = currentPersonalInfo.toBuilder()
      .updatedDate(new Date())
      .ceoFullName(personalInfo.getCeoFullName())
      .note(personalInfo.getNote())
      .organizationAddress(personalInfo.getOrganizationAddress())
      .organizationCity(personalInfo.getOrganizationCity())
      .organizationName(personalInfo.getOrganizationName())
      .organizationBankAccount(personalInfo.getOrganizationBankAccount())
      .organizationEmailAddress(personalInfo.getOrganizationEmailAddress())
      .organizationPostCode(personalInfo.getOrganizationPostCode())
      .organizationPhoneNumber(personalInfo.getOrganizationPhoneNumber())
      .organizationBankBIC(personalInfo.getOrganizationBankBIC())
      .vatNumber(personalInfo.getVatNumber())
      .financeCharge(personalInfo.getFinanceCharge())
      .logoUploadId(currentLogoId)
      .signatureUploadId(currentSignatureId)
      .build();

    notificationService.sendEvent("Personal info updated", PERSONAL_INFO_UPDATED, personalInfo.getId());
    return repository.save(updated);
  }

  public PersonalInfo get() {
    return getOptional().orElseGet(PersonalInfo.builder()::build);
  }

  public Optional<PersonalInfo> getOptional() {
    return repository.findAll().stream().findFirst();
  }

}
