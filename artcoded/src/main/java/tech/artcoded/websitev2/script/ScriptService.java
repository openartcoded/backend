package tech.artcoded.websitev2.script;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.notification.NotificationService;
import tech.artcoded.websitev2.utils.helper.IdGenerators;

import static java.nio.file.StandardWatchEventKinds.*;

@Service
@Slf4j
public class ScriptService {
  public static final String NOTIFICATION_POLYGLOT_EXCEPTION = "POLYGLOT_EXCEPTION";
  private final ScriptProcessorFactory scriptProcessorFactory;
  private final NotificationService notificationService;
  private List<Script> loadedScripts = Collections.synchronizedList(new ArrayList<>());

  @org.springframework.beans.factory.annotation.Value("${application.script.pathToScripts}")
  private String pathToScripts;

  public ScriptService(ScriptProcessorFactory scriptProcessorFactory,
      NotificationService notificationService) {
    this.notificationService = notificationService;
    this.scriptProcessorFactory = scriptProcessorFactory;
  }

  private Optional<Script> load(String script, String filePath) {
    try {
      log.debug("loading script {}", script);
      var ctx = scriptProcessorFactory.createContext();
      eval(ctx, script);
      var jsInstance = eval(ctx, "new Script()");
      var id = jsInstance.getMember("id").asString();
      var enabled = jsInstance.getMember("enabled").asBoolean();
      var consumeEvent = jsInstance.getMember("consumeEvent").asBoolean();
      var name = jsInstance.getMember("name").asString();
      var description = jsInstance.getMember("description").asString();
      var processMethod = jsInstance.getMember("process");
      var builder = Script.builder()
          .id(id)
          .name(name)
          .filePath(filePath)
          .context(ctx)
          .description(description)
          .consumeEvent(consumeEvent)
          .enabled(enabled);
      if (!enabled) {
        log.info("script {} disabled.", name);
        ctx.close();
        return Optional.of(builder.context(null).build());
      }

      log.info("loaded script => {}", name);

      return Optional.of(builder.processMethod(processMethod).instance(jsInstance).build());
    } catch (Exception ex) {
      log.error("failed to load script.", ex);
      return Optional.empty();
    }
  }

  private Value eval(Context ctx, String script) {
    return ctx.eval("js", script);
  }

  public List<Script> getScripts() {
    return Collections.unmodifiableList(loadedScripts);
  }

  @PostConstruct
  private void loadScripts() throws Exception {
    var dirScripts = new File(pathToScripts);
    if (!dirScripts.exists()) {
      dirScripts.mkdirs();
    }
    var scriptFiles = FileUtils.listFiles(dirScripts, new String[] { "js" }, true);

    for (var scriptFile : scriptFiles) {
      var scriptStr = FileUtils.readFileToString(scriptFile, StandardCharsets.UTF_8);
      load(scriptStr, scriptFile.getAbsolutePath()).ifPresent(loadedScripts::add);
    }

    log.info("thread.");
    FileAlterationObserver observer = new FileAlterationObserver(dirScripts,
        f -> "js".equals(FilenameUtils.getExtension(f.getName())));

    log.info("start script watcher for path {}", pathToScripts);
    observer.addListener(new FileAlterationListenerAdaptor() {
      private void loadFile(File file) {
        try {
          log.info("Script Created: {}", file.getName());
          var optionalScript = load(FileUtils.readFileToString(file, StandardCharsets.UTF_8),
              file.getAbsolutePath());
          if (optionalScript.isPresent()) {
            var newScript = optionalScript.get();
            loadedScripts.add(newScript);
          }

        } catch (Exception ex) {
          log.error("could not load file", ex);
        }
      }

      private void unloadFile(File file) {
        // unload script
        log.info("unload script {}", file.getAbsolutePath());
        loadedScripts.stream()
            .filter(s -> file.getAbsolutePath().equals(s.getFilePath())).findFirst()
            .ifPresent(script -> {
              if (script.getContext() != null) {
                script.getContext().close(true);
              }
              loadedScripts.remove(loadedScripts.indexOf(script));
            });
      }

      @Override
      public void onFileCreate(File file) {
        loadFile(file);
      }

      @Override
      public void onFileChange(File file) {
        unloadFile(file);
        loadFile(file);
      }

      @Override
      public void onFileDelete(File file) {
        unloadFile(file);
      }
    });
    FileAlterationMonitor monitor = new FileAlterationMonitor(500, observer);
    try {
      monitor.start();
    } catch (Exception ex) {
      log.error("watcher script exception.", ex);
      notificationService.sendEvent("Script error: watcher thread failed. check the logs.",
          NOTIFICATION_POLYGLOT_EXCEPTION, IdGenerators.get());

    }
  }

  @PreDestroy
  private void unloadScipts() {
    for (var loadedScript : loadedScripts) {
      var ctx = loadedScript.getContext();
      if (ctx != null) {
        log.info("unload script {}", loadedScript.getName());
        ctx.close(true);
      }
    }
  }

}
