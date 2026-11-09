package tech.artcoded.websitev2.action;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

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
public class ActionRequest implements Serializable {
    /**
     * Requests go through JMS as JSON text: camel-jms no longer sends java serialized objects (ObjectMessage) by
     * default, and JSON is safer anyway.
     */
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            // missing flags (sendMail...) are false, like with Jackson 2
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();

    private String actionKey;
    private boolean sendMail;
    private boolean sendSms;
    private boolean persistResult;
    private List<ActionParameter> parameters;

    public String toJson() {
        return MAPPER.writeValueAsString(this);
    }

    public static ActionRequest fromJson(String json) {
        return MAPPER.readValue(json, ActionRequest.class);
    }
}
