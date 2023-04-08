package tech.artcoded.websitev2.processor;

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

  private String name;
  private String description;
  private boolean enabled;

}
