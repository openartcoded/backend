package tech.artcoded.websitev2.event;

import org.junit.jupiter.api.Test;

import com.oracle.truffle.js.runtime.JSContextOptions;
import com.oracle.truffle.js.scriptengine.GraalJSScriptEngine;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import javax.script.Invocable;
import javax.script.ScriptEngine;

public class GraalVmTest {

  @Test
  public void helloWorld() throws Exception {
    var ctxConfig = Context.newBuilder("js")
        .allowHostAccess(HostAccess.ALL)
        .allowExperimentalOptions(true)
        .option(JSContextOptions.INTEROP_COMPLETE_PROMISES_NAME, "true")
        .allowHostClassLookup(s -> true)
        .option("js.ecmascript-version", "2022");
    GraalJSScriptEngine engine = GraalJSScriptEngine.create(null, ctxConfig);
    engine.eval("console.log('hello world!')");
    String someName = "nordine";
    // var bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
    engine.put("person", someName);
    engine.eval("console.log(`hello ${person}!`)");
    engine.put("personService", new SomeService());
    engine.createBindings();
    engine.eval("console.log(personService.greetings('world2'))");

    // call javascript from java
    engine.eval("""
        function greetings(name) {
          return "Hello " + name;
        }
        function helloWorld() {
          return "Hello world!";
        }
        """);
    // Invocable invocable = (Invocable) engine;
    var ctx = engine.getPolyglotContext();
    String result = ctx.eval("js", "helloWorld()").asString();
    assertEquals(result, "Hello world!");
    result = ctx.eval("js", "greetings('Nordine')").asString();
    assertEquals(result, "Hello Nordine");

    // test class
    ctx.eval("js", """
        class Script {
          get id() {
            return "92f0c3e6-ecc6-49b9-9043-4ca638462345";
          }
          get name() {
            return 'Hello world';
          }
          get description() {
            return `An example of script.`;
          }
          process(payload) {
            const event = JSON.parse(payload);
            return `${event.name}-${this.id}-${this.name}-${this.description}`;
          }
        }
        """);
    Value script = ctx.eval("js", "new Script()");

    var id = script.getMember("id");
    assertEquals(id.asString(), "92f0c3e6-ecc6-49b9-9043-4ca638462345");
    var processMethod = script.getMember("process");

    var processed = processMethod.execute("{\"name\": \"Nordine\"}");

    assertEquals("Nordine-92f0c3e6-ecc6-49b9-9043-4ca638462345-Hello world-An example of script.",
        processed.asString());

  }

  public class SomeService {
    public String greetings(String name) {
      return "Hello " + name;
    }
  }

}
