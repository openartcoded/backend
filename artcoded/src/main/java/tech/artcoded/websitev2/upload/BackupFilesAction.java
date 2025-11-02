package tech.artcoded.websitev2.upload;

import lombok.extern.slf4j.Slf4j;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;
import tech.artcoded.websitev2.action.*;
import tech.artcoded.websitev2.utils.helper.IdGenerators;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static java.time.format.DateTimeFormatter.ofPattern;

@Component
@Slf4j
public class BackupFilesAction implements Action {
    public static final String ACTION_KEY = "BACKUP_FILES_ACTION";
    private final IFileUploadService fileUploadService;
    private final BuildProperties buildProperties;

    @Value("${application.upload.pathToBackup}")
    private String pathToBackupFiles;

    public BackupFilesAction(IFileUploadService fileUploadService, BuildProperties buildProperties) {
        this.fileUploadService = fileUploadService;
        this.buildProperties = buildProperties;
    }

    public static ActionMetadata getDefaultMetadata() {
        return ActionMetadata.builder().key(ACTION_KEY).title("Backup Files")
                .description("An action to backup all the files within the server").allowedParameters(List.of())
                .defaultCronValue("0 0 3 * * *").build();
    }

    @Override
    public ActionResult run(List<ActionParameter> parameters) {

        Date date = new Date();
        var resultBuilder = this.actionResultBuilder(parameters).startedDate(date);

        List<String> messages = new ArrayList<>();
        try {
            String archiveName = buildProperties.getVersion().concat("-")
                    .concat(ofPattern("yyyy-MM-dd-HH-mm-ss").format(LocalDateTime.now()));

            File tempDirectory = FileUtils.getTempDirectory();
            File folder = new File(tempDirectory, RandomStringUtils.secure().nextAlphanumeric(6));
            List<FileUpload> uploads = fileUploadService.findAll();
            log.debug("create temp folder: {}", folder.mkdirs());

            messages.add("current upload count: %s".formatted(uploads.size()));
            for (FileUpload upload : uploads) {
                var fileToSave = new File(folder, "%s_%s".formatted(upload.getId(), upload.getOriginalFilename()));
                try (InputStream is = fileUploadService.uploadToInputStream(upload)) {
                    FileUtils.copyToFile(is, fileToSave);
                    messages.add("file %s has been copied".formatted(upload.getOriginalFilename()));
                }
            }
            File zipFile = new File(tempDirectory, archiveName.concat(".zip"));

            ZipParameters zipParameters = new ZipParameters();
            zipParameters.setIncludeRootFolder(false);

            try (var zip = new ZipFile(zipFile)) {
                zip.addFolder(folder, zipParameters);
            }

            File backupFolder = getBackupFolder();

            zipLoop: for (File existingZipFile : FileUtils.listFiles(backupFolder, new String[] { "zip" }, false)) {
                File tempDir = new File(tempDirectory, IdGenerators.get());
                log.debug("mkdir tempdir {}", tempDir.mkdirs());

                try (var existingZip = new ZipFile(existingZipFile)) {
                    existingZip.extractAll(tempDir.getAbsolutePath());
                }
                boolean contentEquals = true;
                // todo this doesn't handle directory I think
                for (File fileToSave : FileUtils.listFiles(folder, null, false)) {
                    var tempFile = new File(tempDir, fileToSave.getName());
                    if (!tempFile.exists()) {
                        FileUtils.deleteDirectory(tempDir);
                        continue zipLoop;
                    }
                    try (var tempIs = new FileInputStream(tempFile);
                            var is = new FileInputStream(fileToSave)) {
                        if (!IOUtils.contentEquals(tempIs, is)) {
                            contentEquals = false;
                            break;
                        }
                    }
                }
                FileUtils.deleteDirectory(tempDir);

                if (contentEquals) {
                    return resultBuilder.finishedDate(new Date()).messages(List.of("zip are identical. done..."))
                            .build();
                }

            }

            FileUtils.moveFileToDirectory(zipFile, backupFolder, true);
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
