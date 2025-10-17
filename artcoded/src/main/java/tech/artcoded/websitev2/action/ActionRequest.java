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
public class ActionRequest implements Serializable {
    private String actionKey;
    private boolean sendMail;
    private boolean sendSms;
    private boolean persistResult;
    private List<ActionParameter> parameters;
}
