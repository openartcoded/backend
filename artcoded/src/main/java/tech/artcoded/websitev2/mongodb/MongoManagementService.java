package tech.artcoded.websitev2.mongodb;

import freemarker.template.Configuration;
import freemarker.template.Template;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.EncryptionMethod;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.stream.slf4j.Slf4jStream;
import tech.artcoded.websitev2.api.helper.IdGenerators;
import tech.artcoded.websitev2.notification.NotificationService;
import tech.artcoded.websitev2.pages.cv.entity.Curriculum;
import tech.artcoded.websitev2.pages.cv.service.CurriculumService;
import tech.artcoded.websitev2.pages.settings.menu.MenuLink;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class MongoManagementService {

  private final AtomicBoolean lock = new AtomicBoolean(false);

  private final Environment environment;
  private final MongoTemplate mongoTemplate;
  private final CurriculumService curriculumService;
  private static final String NOTIFICATION_TYPE_RESTORE = "RESTORE_DUMP";
  private static final String NOTIFICATION_TYPE_DUMP = "NEW_DUMP";
  private static final String NOTIFICATION_DOWNLOAD_DUMP = "NEW_DUMP_DOWNLOAD";
  private final NotificationService notificationService;
  private final Configuration configuration;

  public MongoManagementService(Environment environment, MongoTemplate mongoTemplate, CurriculumService curriculumService, NotificationService notificationService, Configuration configuration) {
    this.environment = environment;
    this.mongoTemplate = mongoTemplate;
    this.curriculumService = curriculumService;
    this.notificationService = notificationService;
    this.configuration = configuration;
  }

  public List<String> dumpList() {
    return FileUtils.listFiles(getDumpFolder(), new String[]{"zip"}, true).stream()
                    .sorted((o1, o2) -> {
                      try {
                        BasicFileAttributes attO1 = Files.readAttributes(o1.toPath(), BasicFileAttributes.class);
                        BasicFileAttributes attO2 = Files.readAttributes(o2.toPath(), BasicFileAttributes.class);
                        return attO2.creationTime().compareTo(attO1.creationTime());
                      }
                      catch (IOException e) {
                        log.error("error while reading attributes", e);
                      }
                      return o2.compareTo(o1);

                    })
                    .map(File::getName).toList();
  }

  private File getDumpFolder() {
    String pathToDumpFolder = environment.getRequiredProperty("application.mongo.pathToDump");
    File dumpFolder = new File(pathToDumpFolder);
    if (!dumpFolder.exists()) {
      dumpFolder.mkdir();
    }
    return dumpFolder;
  }

  private File fetchArchive(String archiveName) {
    return new File(getDumpFolder(), archiveName);
  }

  @SneakyThrows
  @Async
  public void restore(String archiveName, String from, String to) {
    this.restoreSynchronous(archiveName, from, to);
  }

  void restoreSynchronous(String archiveName, String from, String to) throws Exception {
    if(this.lock.get()) {
      // first we do a dump
      this.dumpSynchronous();
    }

    if (lock.getAndSet(true)) {
      throw new RuntimeException("Cannot perform action. Cause: Lock set!");
    }

    // TODO workaround because of duplicates for some reason
    // TODO BUGFIX most welcome
    mongoTemplate.dropCollection(MenuLink.class);
    mongoTemplate.dropCollection(Curriculum.class);
    curriculumService.evictCache();
    // TODO

    File archive = fetchArchive(archiveName);
    File unzip = new File(FileUtils.getTempDirectoryPath(), IdGenerators.get());
    unzip.mkdir();

    new ZipFile(archive.getAbsolutePath()).extractAll(unzip.getAbsolutePath());

    Map<String, String> templateVariables = Map.of(
            "username", environment.getRequiredProperty("spring.data.mongodb.username"),
            "password", environment.getRequiredProperty("spring.data.mongodb.password"),
            "adminDatabase", environment.getRequiredProperty("spring.data.mongodb.authentication-database"),
            "host", environment.getRequiredProperty("spring.data.mongodb.host"),
            "port", environment.getRequiredProperty("spring.data.mongodb.port"),
            "from", from,
            "to", to
    );

    Template template = configuration.getTemplate("mongo-restore.ftl");
    String restoreScript = FreeMarkerTemplateUtils.processTemplateIntoString(template, templateVariables);

    ProcessResult result = new ProcessExecutor().commandSplit(restoreScript).directory(unzip)
                                                .redirectOutput(Slf4jStream.of(log).asInfo()).execute();

    log.info("Exit code : {}", result.getExitValue());

    FileUtils.deleteDirectory(unzip);

    this.notificationService.sendEvent("Restore db from '%s' to '%s' with archive : %s".formatted(from, to, archiveName), NOTIFICATION_TYPE_RESTORE, IdGenerators.get());

    lock.set(false);
  }


  @SneakyThrows
  @Async
  public void dump() {
    this.dumpSynchronous();
  }

  @SneakyThrows
  void dumpSynchronous(){

    if (lock.getAndSet(true)) {
      throw new RuntimeException("Cannot perform action. Cause: Lock set!");
    }

    String dateNow = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss").format(LocalDateTime.now());
    File tempDirectory = FileUtils.getTempDirectory();
    File folder = new File(tempDirectory, dateNow);

    folder.mkdirs();

    Map<String, String> templateVariables = Map.of(
            "username", environment.getRequiredProperty("spring.data.mongodb.username"),
            "password", environment.getRequiredProperty("spring.data.mongodb.password"),
            "adminDatabase", environment.getRequiredProperty("spring.data.mongodb.authentication-database"),
            "host", environment.getRequiredProperty("spring.data.mongodb.host"),
            "port", environment.getRequiredProperty("spring.data.mongodb.port"),
            "dbName", environment.getRequiredProperty("spring.data.mongodb.database")
    );

    Template template = configuration.getTemplate("mongo-dump.ftl");
    String dumpScript = FreeMarkerTemplateUtils.processTemplateIntoString(template, templateVariables);

    ProcessResult result = new ProcessExecutor().commandSplit(dumpScript).directory(folder)
            .redirectOutput(Slf4jStream.of(log).asInfo()).execute();

    log.info("Exit code : {}", result.getExitValue());

    File zipFile = new File(tempDirectory, dateNow.concat(".zip"));
    ZipParameters zipParameters = new ZipParameters();
    zipParameters.setIncludeRootFolder(false);
    new ZipFile(zipFile).addFolder(folder, zipParameters);

    File dumpFolder = getDumpFolder();
    FileUtils.moveFileToDirectory(zipFile, dumpFolder, true);
    FileUtils.cleanDirectory(tempDirectory);

    log.info("Added file {} to {}", dateNow.concat(".zip"), dumpFolder.getAbsolutePath());
    this.notificationService.sendEvent("New Dump: %s".formatted(dateNow.concat(".zip")), NOTIFICATION_TYPE_DUMP, IdGenerators.get());

    lock.set(false);
  }

  @SneakyThrows
  public byte[] download(String archiveName) {
    String password = RandomStringUtils.randomGraph(8);
    File archive = fetchArchive(archiveName);
    File zipFile = new File(FileUtils.getTempDirectoryPath(), IdGenerators.get().concat(".zip"));
    ZipParameters zipParameters = new ZipParameters();
    zipParameters.setIncludeRootFolder(false);
    zipParameters.setEncryptFiles(true);
    zipParameters.setEncryptionMethod(EncryptionMethod.AES);
    new ZipFile(zipFile, password.toCharArray()).addFile(archive, zipParameters);
    byte[] bytes = FileUtils.readFileToByteArray(zipFile);
    FileUtils.deleteQuietly(zipFile);
    this.notificationService.sendEvent("New download request with password %s".formatted(password), NOTIFICATION_DOWNLOAD_DUMP, IdGenerators.get());
    return bytes;
  }
}
