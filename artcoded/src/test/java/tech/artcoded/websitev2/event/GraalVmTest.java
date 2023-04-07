package tech.artcoded.websitev2.event;

import org.junit.jupiter.api.Test;

import com.oracle.truffle.js.scriptengine.GraalJSScriptEngine;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;

import static org.junit.Assert.assertEquals;

import javax.script.Invocable;
import javax.script.ScriptEngine;

public class GraalVmTest {

  @Test
  public void helloWorld() throws Exception {
    var ctxConfig = Context.newBuilder("js")
        .allowHostAccess(HostAccess.ALL)
        .allowHostClassLookup(s -> true)
        .option("js.ecmascript-version", "2022");
    ScriptEngine engine = GraalJSScriptEngine.create(null, ctxConfig);
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
    Invocable invocable = (Invocable) engine;
    Object result = invocable.invokeFunction("helloWorld");
    assertEquals(result, "Hello world!");
    result = invocable.invokeFunction("greetings", "Nordine");
    assertEquals(result, "Hello Nordine");
  }

  public class SomeService {
    public String greetings(String name) {
      return "Hello " + name;
    }
  }

}
