package tech.artcoded.websitev2.action;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ActionParameter implements Serializable {
  private String key;
  private boolean required;
  private String value;
  private ActionParameterType parameterType;
  private String description;
  @Builder.Default
  private List<String> options = List.of();
}
