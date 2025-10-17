package tech.artcoded.websitev2.action;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ActionMetadata {
    private String key;
    private String title;
    private String description;
    private List<ActionParameter> allowedParameters;
    private String defaultCronValue;
}
