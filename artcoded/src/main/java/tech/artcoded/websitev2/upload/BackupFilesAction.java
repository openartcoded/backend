package tech.artcoded.websitev2.upload;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tech.artcoded.websitev2.action.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.apache.commons.io.FilenameUtils.normalize;

@Component
@Slf4j
public class BackupFilesAction implements Action {
  public static final String ACTION_KEY = "BACKUP_FILES_ACTION";
  private final FileUploadService fileUploadService;

  @Value("${application.upload.pathToBackup}")
  private String pathToBackupFiles;

  public BackupFilesAction(FileUploadService fileUploadService) {
    this.fileUploadService = fileUploadService;
  }

  public static ActionMetadata getDefaultMetadata() {
    return ActionMetadata.builder()
      .key(ACTION_KEY)
      .title("Backup Files")
      .description("An action to backup all the files within the server")
      .allowedParameters(List.of())
      .defaultCronValue("0 0 3 * * *")
      .build();
  }

  @Override
  public ActionResult run(List<ActionParameter> parameters) {

    Date date = new Date();
    var resultBuilder = this.actionResultBuilder(parameters).startedDate(date);

    List<String> messages = new ArrayList<>();
    try {
      List<FileUpload> uploads = fileUploadService.findAll();
      messages.add("current upload count: %s".formatted(uploads.size()));
      for (FileUpload upload : uploads) {
        var fileName = "%s_%s".formatted(upload.getId(), normalize(upload.getOriginalFilename().replace(' ', '_')));
        var fileToSave = new File(getBackupFolder(), fileName);
        try (InputStream is = fileUploadService.uploadToInputStream(upload)) {
          if (fileToSave.exists()) {
            try (var existingIs = new FileInputStream(fileToSave)) {
              if (IOUtils.contentEquals(is, existingIs)) {
                log.debug("file {} already exists, continue", fileName);
                continue;
              }
            }
          }
          FileUtils.copyToFile(is, fileToSave);
          messages.add("file %s has been copied".formatted(fileName));
        }
      }
      messages.add("done.");
      return resultBuilder.finishedDate(new Date()).messages(messages).build();

    } catch (Exception e) {
      log.error("error while executing action", e);
      messages.add("error, see logs: %s".formatted(e.getMessage()));
      return resultBuilder.messages(messages).finishedDate(new Date()).status(StatusType.FAILURE).build();
    }
  }

  @Override
  public ActionMetadata getMetadata() {
    return getDefaultMetadata();
  }

  @Override
  public String getKey() {
    return ACTION_KEY;
  }

  private File getBackupFolder() {
    File backupFolder = new File(pathToBackupFiles);
    if (!backupFolder.exists()) {
      boolean result = backupFolder.mkdirs();
      log.debug("creating backup folder: {}", result);
    }
    return backupFolder;
  }
}
