package tech.artcoded.websitev2.upload;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.gridfs.model.GridFSFile;
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

@Component
@Slf4j
public class BackupFilesAction implements Action {
  public static final String ACTION_KEY = "BACKUP_FILES_ACTION";
  private final FileUploadService fileUploadService;
  private final ObjectMapper mapper = new ObjectMapper();

  @Value("${application.mongo.pathToBackup}")
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
      List<GridFSFile> uploads = fileUploadService.findAll();
      messages.add("current upload count: %s".formatted(uploads.size()));
      for (GridFSFile upload : uploads) {
        var dto = fileUploadService.toFileUploadDto(upload);
        var metadata = mapper.readValue(dto.getMetadata(), FileUploadMetadata.class);
        String originalFilename = metadata.getOriginalFilename();
        var fileToSave = new File(getBackupFolder(), originalFilename);
        try (InputStream is = fileUploadService.uploadToInputStream(upload)) {
          if (fileToSave.exists()) {
            try (var existingIs = new FileInputStream(fileToSave)) {
              if (IOUtils.contentEquals(is, existingIs)) {
                messages.add("file %s already exists, continue".formatted(originalFilename));
                continue;
              }
            }
          }
          FileUtils.copyToFile(is, fileToSave);
          messages.add("file %s has been copied".formatted(originalFilename));
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
