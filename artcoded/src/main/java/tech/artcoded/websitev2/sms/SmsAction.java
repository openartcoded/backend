package tech.artcoded.websitev2.sms;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.action.Action;
import tech.artcoded.websitev2.action.ActionMetadata;
import tech.artcoded.websitev2.action.ActionParameter;
import tech.artcoded.websitev2.action.ActionParameterType;
import tech.artcoded.websitev2.action.ActionResult;
import tech.artcoded.websitev2.action.StatusType;

@Component
@Slf4j
public class SmsAction implements Action {

    public static final String ACTION_KEY = "SMS_ACTION";
    public static final String PARAMETER_PHONE_NUMBER = "PARAMETER_PHONE_NUMBER";
    public static final String PARAMETER_MESSAGE = "PARAMETER_MESSAGE";

    private final SmsService smsService;

    @Inject
    public SmsAction(SmsService smsService) {
        this.smsService = smsService;
    }

    public static ActionMetadata getDefaultMetadata() {
        return ActionMetadata.builder().key(ACTION_KEY).title("Sms Action").description("An action to send an sms")
                .allowedParameters(List.of(
                        ActionParameter.builder().key(PARAMETER_PHONE_NUMBER).parameterType(ActionParameterType.STRING)
                                .required(false)
                                .description(
                                        "The phone number (e.g +32/487.11.11.11). Only these characters are allowed")
                                .build(),
                        ActionParameter.builder().key(PARAMETER_MESSAGE).parameterType(ActionParameterType.STRING)
                                .required(false).description("The message to send").build()))
                .defaultCronValue("0 30 0 * * *").build();

    }

    @Override
    public ActionMetadata getMetadata() {
        return getDefaultMetadata();
    }

    @Override
    public String getKey() {
        return ACTION_KEY;
    }

    @Override
    public ActionResult run(List<ActionParameter> parameters) {
        Date date = new Date();
        var resultBuilder = this.actionResultBuilder(parameters).startedDate(date);

        List<String> messages = new ArrayList<>();
        try {
            messages.add("starting action");
            var phoneNumber = parameters.stream().filter(p -> PARAMETER_PHONE_NUMBER.equals(p.getKey()))
                    .map(ActionParameter::getValue).filter(p -> StringUtils.isNotEmpty(p)).findFirst()
                    .orElseThrow(() -> new RuntimeException("phone number missing"));
            messages.add("phone number: " + phoneNumber);

            var message = parameters.stream().filter(p -> PARAMETER_MESSAGE.equals(p.getKey()))
                    .map(ActionParameter::getValue).filter(p -> StringUtils.isNotEmpty(p)).findFirst()
                    .orElseThrow(() -> new RuntimeException("message missing"));
            messages.add("message: " + message);

            smsService.send(Sms.builder().phoneNumber(phoneNumber).message(message).build());
            return resultBuilder.finishedDate(new Date()).messages(messages).build();

        } catch (Exception e) {
            log.error("error while executing action", e);
            messages.add("error, see logs: %s".formatted(e.getMessage()));
            return resultBuilder.messages(messages).finishedDate(new Date()).status(StatusType.FAILURE).build();
        }
    }

}
