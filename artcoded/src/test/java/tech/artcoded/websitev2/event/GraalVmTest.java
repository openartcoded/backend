package tech.artcoded.websitev2.event;

import org.junit.jupiter.api.Test;

import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.utils.common.LogOutputStream;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import org.zeroturnaround.exec.stream.slf4j.Slf4jInfoOutputStream;
import static org.junit.Assert.assertEquals;

@Slf4j(topic = "TestScriptLogger")
public class GraalVmTest {

    @Test
    public void helloWorld() throws Exception {
        var ctxConfig = Context.newBuilder("js").allowHostAccess(HostAccess.ALL).out(new LogOutputStream(log))
                .err(new LogOutputStream(log)).logHandler(new Slf4jInfoOutputStream(log))
                .option("engine.WarnInterpreterOnly", "false")
                // .allowExperimentalOptions(true)
                // .option(JSContextOptions.INTEROP_COMPLETE_PROMISES_NAME, "true")
                // .option("js.print", "true")
                // .option("js.load", "true")
                // .option("js.syntax-extensions", "true")
                // .option("js.global-arguments", "true")
                .allowHostClassLookup(_ -> true).option("js.ecmascript-version", "2022");

        // GraalJSScriptEngine engine = GraalJSScriptEngine.create(null, ctxConfig);
        // engine.getContext().setWriter(new OutputStreamWriter(new
        // LogOutputStream(log)));
        // var ctx = engine.getPolyglotContext();
        var ctx = ctxConfig.build();
        var bindings = ctx.getBindings("js");
        ctx.eval("js", "console.log('hello world!')");
        String someName = "nordine";
        // var bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
        bindings.putMember("person", someName);
        ctx.eval("js", "console.log(`hello ${person}!`)");
        bindings.putMember("personService", new SomeService());
        ctx.eval("js", "console.log(personService.greetings('world2'))");
        // call javascript from java
        ctx.eval("js", """
                function greetings(name) {
                  return "Hello " + name;
                }
                function helloWorld() {
                  return "Hello world!";
                }
                """);
        // Invocable invocable = (Invocable) engine;

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
                    console.log(`${event.name}-${this.id}-${this.name}-${this.description}`);
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
