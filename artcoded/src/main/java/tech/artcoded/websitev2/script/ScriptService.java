package tech.artcoded.websitev2.script;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
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
      if (!enabled) {
        log.info("script {} disabled.", name);
        ctx.close();
        return Optional.of(Script.builder()
            .id(id)
            .name(name)
            .filePath(filePath)
            .context(ctx)
            .description(description)
            .enabled(enabled)
            .consumeEvent(consumeEvent).build());
      }

      log.info("loaded script => {}", name);

      return Optional.of(Script.builder().id(id)
          .name(name)
          .description(description)
          .consumeEvent(consumeEvent)
          .enabled(enabled)
          .processMethod(processMethod).instance(jsInstance).build());
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
  @SuppressWarnings("unchecked")
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

    // watcher for new scripts
    Thread.startVirtualThread(() -> {
      try {
        var pathToDirScripts = dirScripts.toPath();
        WatchService watcher = FileSystems.getDefault().newWatchService();
        WatchKey key = pathToDirScripts.register(watcher,
            ENTRY_CREATE,
            ENTRY_DELETE,
            ENTRY_MODIFY);
        for (;;) {
          for (WatchEvent<?> event : key.pollEvents()) {
            WatchEvent.Kind<?> kind = event.kind();
            if (kind == OVERFLOW) {
              continue;
            }
            WatchEvent<Path> ev = (WatchEvent<Path>) event;
            Path filename = ev.context();
            Path child = pathToDirScripts.resolve(filename);

            var file = child.toFile();
            if (kind == ENTRY_DELETE || key == ENTRY_MODIFY) {
              // unload script
              log.info("unload script {}", file.getAbsolutePath());
              this.loadedScripts.stream()
                  .filter(s -> file.getAbsolutePath().equals(s.getFilePath())).findFirst()
                  .ifPresent(script -> {
                    script.getContext().close(true);
                    this.loadedScripts.remove(this.loadedScripts.indexOf(script));
                  });

            }
            if (key == ENTRY_CREATE || key == ENTRY_MODIFY) {
              // check if file is a javascript file
              try {
                if (!Files.probeContentType(child).equals("application/javascript")) {
                  log.warn("file '%s'" +
                      " is not a javascript file.%n", filename);
                  continue;
                }
              } catch (Exception ex) {
                log.error("could not determine content type for file", ex);
                continue;
              }

              var optionalScript = this.load(FileUtils.readFileToString(file, StandardCharsets.UTF_8),
                  file.getAbsolutePath());
              if (optionalScript.isPresent()) {
                var newScript = optionalScript.get();
                this.loadedScripts.add(newScript);
              }

            }

          }
          boolean valid = key.reset();

          if (!valid) {
            break;
          }

        }

      } catch (Exception ex) {
        log.error("watcher script exception.", ex);
        notificationService.sendEvent("Script error: watcher thread failed. check the logs.",
            NOTIFICATION_POLYGLOT_EXCEPTION, IdGenerators.get());
      }
    });

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
