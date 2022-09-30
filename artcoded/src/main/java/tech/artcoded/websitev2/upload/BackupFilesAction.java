package tech.artcoded.websitev2.upload;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;
import tech.artcoded.websitev2.action.*;

import java.io.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static java.time.format.DateTimeFormatter.ofPattern;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;

@Component
@Slf4j
public class BackupFilesAction implements Action {
  public static final String ACTION_KEY = "BACKUP_FILES_ACTION";
  private final FileUploadService fileUploadService;
  private final BuildProperties buildProperties;

  @Value("${application.upload.pathToBackup}")
  private String pathToBackupFiles;

  public BackupFilesAction(FileUploadService fileUploadService, BuildProperties buildProperties) {
    this.fileUploadService = fileUploadService;
    this.buildProperties = buildProperties;
  }

  public static ActionMetadata getDefaultMetadata() {
    return ActionMetadata.builder().key(ACTION_KEY).title("Backup Files").description("An action to backup all the files within the server").allowedParameters(List.of()).defaultCronValue("0 0 3 * * *").build();
  }

  @Override
  public ActionResult run(List<ActionParameter> parameters) {

    Date date = new Date();
    var resultBuilder = this.actionResultBuilder(parameters).startedDate(date);

    List<String> messages = new ArrayList<>();
    try {
      String archiveName = buildProperties.getVersion().concat("-").concat(ofPattern("yyyy-MM-dd-HH-mm-ss").format(LocalDateTime.now()));

      File tempDirectory = FileUtils.getTempDirectory();
      File folder = new File(tempDirectory, randomAlphanumeric(6));
      List<FileUpload> uploads = fileUploadService.findAll();
      log.debug("create temp folder: {}", folder.mkdirs());

      File tarFile = new File(tempDirectory, archiveName.concat(".tar"));

      messages.add("current upload count: %s".formatted(uploads.size()));
      for (FileUpload upload : uploads) {
        var fileToSave = new File(folder, "%s_%s".formatted(upload.getId(), upload.getOriginalFilename()));
        try (InputStream is = fileUploadService.uploadToInputStream(upload)) {
          FileUtils.copyToFile(is, fileToSave);
          messages.add("file %s has been copied".formatted(upload.getOriginalFilename()));
        }
      }

      try (OutputStream fOut = new FileOutputStream(tarFile);
           BufferedOutputStream buffOut = new BufferedOutputStream(fOut);
           TarArchiveOutputStream tOut = new TarArchiveOutputStream(buffOut)) {
        tOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
        for (File f : FileUtils.listFiles(folder, null, true)) {
          TarArchiveEntry tarEntry = new TarArchiveEntry(f);
          tOut.putArchiveEntry(tarEntry);
          tOut.closeArchiveEntry();
        }

        tOut.finish();
      }

      File backupFolder = getBackupFolder();

      for (File existingZipFile : FileUtils.listFiles(backupFolder, new String[]{"tar"}, false)) {
        try (var existingZipIS = new FileInputStream(existingZipFile); var zipIS = new FileInputStream(tarFile)) {
          if (IOUtils.contentEquals(existingZipIS, zipIS)) {
            messages.add("tar are identical. done...");
            return resultBuilder.finishedDate(new Date()).messages(messages).build();
          }
        }
      }

      FileUtils.moveFileToDirectory(tarFile, backupFolder, true);
      FileUtils.cleanDirectory(tempDirectory);

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
