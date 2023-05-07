package tech.artcoded.websitev2.script;

import java.util.Optional;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Script {
  private String id;

  @JsonIgnore
  private Value instance;

  @JsonIgnore
  private Value processMethod;

  @JsonIgnore
  private Context context;

  private String name;
  @JsonIgnore
  private String filePath;
  private String description;
  private boolean enabled;
  private boolean consumeEvent;
  @JsonIgnore
  private Thread oneShotScriptThread;

}
