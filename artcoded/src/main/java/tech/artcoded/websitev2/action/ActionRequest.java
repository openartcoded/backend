package tech.artcoded.websitev2.action;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

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
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private String actionKey;
    private boolean sendMail;
    private boolean sendSms;
    private boolean persistResult;
    private List<ActionParameter> parameters;

    @SneakyThrows
    public String toJson() {
        return MAPPER.writeValueAsString(this);
    }

    @SneakyThrows
    public static ActionRequest fromJson(String json) {
        return MAPPER.readValue(json, ActionRequest.class);
    }
}
