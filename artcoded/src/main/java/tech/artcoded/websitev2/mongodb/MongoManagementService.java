package tech.artcoded.websitev2.mongodb;

import static java.lang.System.currentTimeMillis;
import static java.time.format.DateTimeFormatter.ofPattern;
import static java.util.Optional.ofNullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.info.BuildProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.stream.slf4j.Slf4jStream;

import freemarker.template.Configuration;
import freemarker.template.Template;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.notification.NotificationService;
import tech.artcoded.websitev2.upload.FileUploadService;
import tech.artcoded.websitev2.utils.helper.CompressionHelper;
import tech.artcoded.websitev2.utils.helper.CompressionHelper.SourceType;
import tech.artcoded.websitev2.utils.helper.IdGenerators;

@Service
@Slf4j
public class MongoManagementService {

  private final BuildProperties buildProperties;

  private Semaphore mutex = new Semaphore(1);

  private final Environment environment;
  private final MongoTemplate mongoTemplate;
  private final CacheManager cacheManager;
  private static final String NOTIFICATION_TYPE_RESTORE = "RESTORE_DUMP";
  private static final String NOTIFICATION_TYPE_DUMP = "NEW_DUMP";
  // private static final String NOTIFICATION_DOWNLOAD_DUMP = "NEW_DUMP_DOWNLOAD";
  private final NotificationService notificationService;
  private final FileUploadService fileUploadService;
  private final Configuration configuration;

  public MongoManagementService(BuildProperties buildProperties, Environment environment,
      MongoTemplate mongoTemplate,
      CacheManager cacheManager,
      NotificationService notificationService,
      FileUploadService fileUploadService, Configuration configuration) {
    this.buildProperties = buildProperties;
    this.environment = environment;
    this.mongoTemplate = mongoTemplate;
    this.cacheManager = cacheManager;
    this.notificationService = notificationService;
    this.fileUploadService = fileUploadService;
    this.configuration = configuration;
  }

  public List<String> dumpList(boolean snapshot) {
    return FileUtils.listFiles(getDumpFolder(snapshot), new String[] { "zip", "gz" }, true).stream()
        .sorted((o1, o2) -> {
          try {
            BasicFileAttributes attO1 = Files.readAttributes(o1.toPath(), BasicFileAttributes.class);
            BasicFileAttributes attO2 = Files.readAttributes(o2.toPath(), BasicFileAttributes.class);
            return attO1.creationTime().compareTo(attO2.creationTime());
          } catch (IOException e) {
            log.error("error while reading attributes", e);
          }
          return o1.compareTo(o2);

        })
        .map(File::getName).toList();
  }

  private File getDumpFolder(boolean snapshot) {
    String pathToDumpFolder = environment
        .getRequiredProperty(snapshot ? "application.mongo.pathToDumpSnapshot" : "application.mongo.pathToDump");
    File dumpFolder = new File(pathToDumpFolder);
    if (!dumpFolder.exists()) {
      boolean result = dumpFolder.mkdirs();
      log.debug("creating dump folder: {}", result);
    }
    return dumpFolder;
  }

  private File fetchArchive(String archiveName, boolean snapshot) {
    return new File(getDumpFolder(snapshot), archiveName);
  }

  public List<String> restore(String archiveName, String to, boolean snapshot) throws Exception {

    // first we do a full dump
    this.dump(snapshot);

    try {
      mutex.acquire();
      // TODO workaround because of duplicates for some reason
      // delete all collections
      mongoTemplate.getCollectionNames()
          .forEach(mongoTemplate::dropCollection);
      // clear all caches
      cacheManager.getCacheNames()
          .forEach(c -> ofNullable(c).filter(StringUtils::isNotEmpty)
              .map(cacheManager::getCache)
              .ifPresent(Cache::clear));

      File archive = fetchArchive(archiveName, snapshot);
      File unzip = new File(FileUtils.getTempDirectoryPath(), IdGenerators.get());
      boolean mkdirResult = unzip.mkdir();
      log.debug("create archive zip temp directory {}", mkdirResult);

      switch (FilenameUtils.getExtension(archive.getName())) {
        case "zip" -> CompressionHelper.unzip(archive.getAbsolutePath(), unzip.getAbsolutePath());
        case "gz" -> CompressionHelper.untar(archive.getAbsolutePath(), unzip.getAbsolutePath());
        default ->
          throw new RuntimeException("format for file %s not valid (zip or tar.gz)".formatted(archive.getName()));
      }

      File fromDirectory = new File(unzip, "dump");

      if (!fromDirectory.exists() || !fromDirectory.isDirectory()) {
        throw new RuntimeException("directory structure of the dumped zip files is incorrect!");
      }
      var from = ofNullable(fromDirectory.listFiles())
          .stream()
          .findFirst()
          .filter(d -> d.length == 1)
          .map(d -> d[0])
          .filter(File::isDirectory)
          .map(File::getName)
          .orElseThrow(() -> new RuntimeException("Could not determine from where to restore"));
      ;

      Map<String, String> templateVariables = Map.of(
          "username", environment.getRequiredProperty("spring.data.mongodb.username"),
          "password", environment.getRequiredProperty("spring.data.mongodb.password"),
          "adminDatabase", environment.getRequiredProperty("spring.data.mongodb.authentication-database"),
          "host", environment.getRequiredProperty("spring.data.mongodb.host"),
          "port", environment.getRequiredProperty("spring.data.mongodb.port"),
          "from", from,
          "to", to);

      Template template = configuration.getTemplate("mongo-restore.ftl");
      String restoreScript = FreeMarkerTemplateUtils.processTemplateIntoString(template, templateVariables);

      ProcessResult result = new ProcessExecutor().readOutput(true)
          .commandSplit(restoreScript).directory(unzip)
          .redirectOutput(Slf4jStream.of(log).asInfo()).execute();

      log.info("Exit code : {}", result.getExitValue());

      if (!snapshot) {
        File uploadFolder = fileUploadService.getUploadFolder();
        File filesFromDirectory = new File(unzip, uploadFolder.getName());
        if (filesFromDirectory.exists() && filesFromDirectory.isDirectory()) {
          FileUtils.moveDirectory(uploadFolder,
              new File(uploadFolder.getAbsolutePath().concat(".backup.") + currentTimeMillis()));
          FileUtils.copyDirectory(filesFromDirectory, uploadFolder);
        } else {
          log.warn("upload directory not found. maybe it's an old dump or a simple snapshot?");
        }
      }

      FileUtils.deleteDirectory(unzip);

      this.notificationService.sendEvent(
          "Restore db from '%s' to '%s' with archive : %s".formatted(from, to, archiveName), NOTIFICATION_TYPE_RESTORE,
          IdGenerators.get());

      return result.getOutput().getLinesAsUTF8();
    } catch (Exception ex) {
      log.error("restore error", ex);
      return List.of(ex.getMessage());
    } finally {
      mutex.release();
    }
  }

  @SneakyThrows
  public List<String> dump(boolean snapshot) {
    try {
      mutex.acquire();
      String archiveName = buildProperties.getVersion().concat("-")
          .concat(ofPattern("yyyy-MM-dd-HH-mm-ss").format(LocalDateTime.now()));
      File tempDirectory = FileUtils.getTempDirectory();
      File toDeleteDirectory = new File(tempDirectory, IdGenerators.get());
      File folder = new File(tempDirectory, archiveName);

      boolean mkdirResult = folder.mkdirs();
      log.debug("create temp dir: {}, and {}", mkdirResult, toDeleteDirectory.mkdirs());

      Map<String, String> templateVariables = Map.of(
          "username", environment.getRequiredProperty("spring.data.mongodb.username"),
          "password", environment.getRequiredProperty("spring.data.mongodb.password"),
          "adminDatabase", environment.getRequiredProperty("spring.data.mongodb.authentication-database"),
          "host", environment.getRequiredProperty("spring.data.mongodb.host"),
          "port", environment.getRequiredProperty("spring.data.mongodb.port"),
          "dbName", environment.getRequiredProperty("spring.data.mongodb.database"));

      Template template = configuration.getTemplate("mongo-dump.ftl");
      String dumpScript = FreeMarkerTemplateUtils.processTemplateIntoString(template, templateVariables);

      ProcessResult result = new ProcessExecutor()
          .readOutput(true)
          .commandSplit(dumpScript).directory(folder)
          .redirectOutput(Slf4jStream.of(log).asInfo()).execute();

      log.info("Exit code : {}", result.getExitValue());

      var uploadFolder = fileUploadService.getUploadFolder();

      // 20240420: swith to tar.gz compression
      File tarGzFile = new File(toDeleteDirectory, archiveName.concat(".tar.gz"));
      CompressionHelper.tar(tarGzFile, snapshot ? List.of(new SourceType(folder,
          true))
          : List.of(
              new SourceType(folder, true),
              new SourceType(uploadFolder, false)));
      File dumpFolder = getDumpFolder(snapshot);
      FileUtils.moveFileToDirectory(tarGzFile, dumpFolder, true);
      FileUtils.deleteDirectory(toDeleteDirectory);

      log.info("Added file {} to {}", archiveName.concat(".tar.gz"), dumpFolder.getAbsolutePath());

      if (!snapshot) {
        this.notificationService.sendEvent("New Dump: %s".formatted(archiveName.concat(".tar.gz")),
            NOTIFICATION_TYPE_DUMP,
            IdGenerators.get());
      } else {
        log.info("snapshot {} created.", archiveName.concat(".tar.gz"));
      }

      return result.getOutput().getLinesAsUTF8();
    } catch (Exception ex) {
      log.error("dump error", ex);
      return List.of(ex.getMessage());
    } finally {
      mutex.release();
    }
  }

  @SneakyThrows
  @Deprecated(since = "2024.2.0")
  public byte[] download(String archiveName, boolean snapshot) {
    // String password = RandomStringUtils.randomGraph(8);
    // File archive = fetchArchive(archiveName, snapshot);
    // File zipFile = new File(FileUtils.getTempDirectoryPath(),
    // IdGenerators.get().concat(".zip"));
    // ZipParameters zipParameters = new ZipParameters();
    // zipParameters.setIncludeRootFolder(false);
    // zipParameters.setEncryptFiles(true);
    // zipParameters.setEncryptionMethod(EncryptionMethod.AES);
    // try (var zip = new ZipFile(zipFile, password.toCharArray())) {
    // zip.addFile(archive, zipParameters);
    // }
    // byte[] bytes = FileUtils.readFileToByteArray(zipFile);
    // FileUtils.deleteQuietly(zipFile);
    // this.notificationService.sendEvent("New download request with password
    // %s".formatted(password),
    // NOTIFICATION_DOWNLOAD_DUMP, IdGenerators.get());
    return new byte[] {};
  }
}
