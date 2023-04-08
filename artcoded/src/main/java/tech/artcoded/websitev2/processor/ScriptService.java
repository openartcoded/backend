package tech.artcoded.websitev2.processor;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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

@Service
@Slf4j
public class ScriptService {
  public static final String NOTIFICATION_POLYGLOT_EXCEPTION = "POLYGLOT_EXCEPTION";
  private final ScriptProcessorFactory scriptProcessorFactory;
  private final NotificationService notificationService;
  private List<Script> loadedScripts = new ArrayList<>();

  @org.springframework.beans.factory.annotation.Value("${application.script.pathToScripts}")
  private String pathToScripts;

  public ScriptService(ScriptProcessorFactory scriptProcessorFactory,
      NotificationService notificationService) {
    this.notificationService = notificationService;
    this.scriptProcessorFactory = scriptProcessorFactory;
  }

  private Optional<Script> load(String script) {
    try {
      log.debug("loading script {}", script);
      var engine = scriptProcessorFactory.createScriptEngine();
      var ctx = engine.getPolyglotContext();
      eval(ctx, script);
      var jsInstance = eval(ctx, "new Script()");
      var id = jsInstance.getMember("id").asString();
      var enabled = jsInstance.getMember("enabled").asBoolean();
      var name = jsInstance.getMember("name").asString();
      var description = jsInstance.getMember("description").asString();
      var processMethod = jsInstance.getMember("process");

      log.info("loaded script => {}", name);

      return Optional.of(Script.builder().id(id)
          .name(name).description(description).enabled(enabled)
          .processMethod(processMethod).instance(jsInstance).build());
    } catch (Exception ex) {
      log.error("failed to load script.", ex);
      notificationService.sendEvent("Failed to load script. See logs.", NOTIFICATION_POLYGLOT_EXCEPTION,
          IdGenerators.get());
      return Optional.empty();
    }
  }

  private Value eval(Context ctx, String script) {
    return ctx.eval("js", script);
  }

  @PostConstruct
  public void loadScripts() throws Exception {
    var dirScripts = new File(pathToScripts);
    if (!dirScripts.exists()) {
      dirScripts.mkdirs();
    }
    var scriptFiles = FileUtils.listFiles(dirScripts, new String[] { "js" }, true);

    for (var scriptFile : scriptFiles) {
      var scriptStr = FileUtils.readFileToString(scriptFile, StandardCharsets.UTF_8);
      load(scriptStr).ifPresent(loadedScripts::add);
    }

  }

  @PreDestroy
  public void unloadScipts() {
    for (var loadedScript : loadedScripts) {
      log.info("unload script {}", loadedScript.getName());
      loadedScript.getInstance().getContext().close();
    }
  }

}
