package tech.artcoded.websitev2.script;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/script")
public class ScriptController {
  private final ScriptService scriptService;

  public ScriptController(ScriptService scriptService) {
    this.scriptService = scriptService;
  }

  public record ScriptRequest(String script) {
  }

  @PostMapping(value = "/run", produces = MediaType.TEXT_PLAIN_VALUE)
  public String runScript(@RequestBody ScriptRequest scriptRequest) {
    return this.scriptService.experimentalRunManually(scriptRequest.script());
  }

  @PostMapping(value = "/bindings", produces = MediaType.APPLICATION_JSON_VALUE)
  public String runScript() {
    var ctx = scriptService.createContext();
    return this.scriptService.displayBindings(ctx);
  }

  @PostMapping
  public List<Script> getScripts() {
    return scriptService.getScripts();
  }
}
