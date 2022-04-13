package tech.artcoded.websitev2.pages.dossier;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tech.artcoded.websitev2.action.Action;
import tech.artcoded.websitev2.action.ActionMetadata;
import tech.artcoded.websitev2.action.ActionParameter;
import tech.artcoded.websitev2.action.ActionResult;
import tech.artcoded.websitev2.action.StatusType;
import tech.artcoded.websitev2.upload.FileUploadService;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static tech.artcoded.websitev2.api.func.CheckedVoidConsumer.toConsumer;

@Service
@Slf4j
public class DossierBackupAction implements Action {
  public static final String ACTION_KEY = "DOSSIER_BACKUP_ACTION";

  @Value("${application.dossier.pathToBackup}")
  private String directoryBackup;

  private final DossierService dossierService;
  private final FileUploadService fileUploadService;

  public DossierBackupAction(DossierService dossierService, FileUploadService fileUploadService) {
    this.dossierService = dossierService;
    this.fileUploadService = fileUploadService;

  }

  @Override
  public ActionResult run(List<ActionParameter> parameters) {
    var resultBuilder = this.actionResultBuilder(parameters);

    List<String> messages = new ArrayList<>();
    try {
      var dossierBackupDirectory = new File(directoryBackup);
      if (!dossierBackupDirectory.exists()) {
        log.debug("dossierBackupDirectory.mkdirs() {}", dossierBackupDirectory.mkdirs());
      }
      dossierService.findByClosedIsTrueAndBackupDateIsNull()
                    .stream().filter(dossier -> StringUtils.isNotEmpty(dossier.getDossierUploadId()))
                    .forEach(dossier -> {
                      var uploadId = dossier.getDossierUploadId();
                      fileUploadService.findOneById(uploadId)
                                       .map(fileUploadService::toMockMultipartFile)
                                       .ifPresent(multipartFile -> {
                                         messages.add("Backing up dossier " + dossier.getName());
                                         toConsumer(() -> multipartFile.transferTo(dossierBackupDirectory)).safeConsume();
                                         dossierService.save(dossier.toBuilder()
                                                                    .backupDate(new Date())
                                                                    .build());

                                       });

                    });
      return resultBuilder.finishedDate(new Date()).status(StatusType.SUCCESS).messages(messages).build();

    }
    catch (Exception e) {
      messages.add("error, see logs: %s".formatted(e.getMessage()));
      return resultBuilder.messages(messages).finishedDate(new Date()).status(StatusType.FAILURE).build();
    }
  }

  @Override
  public ActionMetadata getMetadata() {
    return ActionMetadata.builder()
                         .key(ACTION_KEY)
                         .title("Dossier Backup Action")
                         .description("An action to perform a backup of a dossier when it is closed.")
                         .allowedParameters(List.of())
                         .defaultCronValue("0 */5 * * * ?")
                         .build();
  }

  @Override
  public String getKey() {
    return ACTION_KEY;
  }
}
