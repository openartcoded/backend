package tech.artcoded.websitev2.action;

import jakarta.annotation.PostConstruct;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Body;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import tech.artcoded.websitev2.pages.mail.MailJobRepository;
import tech.artcoded.websitev2.pages.personal.PersonalInfo;
import tech.artcoded.websitev2.pages.personal.PersonalInfoService;
import tech.artcoded.websitev2.sms.Sms;
import tech.artcoded.websitev2.sms.SmsService;
import tech.artcoded.websitev2.template.OaTemplateRenderer;
import tech.artcoded.websitev2.template.OaTemplateType;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static java.util.Optional.ofNullable;
import static tech.artcoded.websitev2.action.ActionService.ACTION_ENDPOINT;

@Component
@Slf4j
public class ActionRouteBuilder extends RouteBuilder {
    private final List<Action> actions;
    private final PersonalInfoService personalInfoService;
    private final MailJobRepository mailJobRepository;
    private final SmsService smsService;
    private final OaTemplateRenderer templateRenderer;
    private final ActionResultRepository actionResultRepository;

    public ActionRouteBuilder(List<Action> actions, PersonalInfoService personalInfoService, MailJobRepository mailRepo,
            SmsService smsService, OaTemplateRenderer templateRenderer, ActionResultRepository actionResultRepository) {
        this.actions = actions;
        this.personalInfoService = personalInfoService;
        this.mailJobRepository = mailRepo;
        this.smsService = smsService;
        this.templateRenderer = templateRenderer;
        this.actionResultRepository = actionResultRepository;
    }

    @Override
    public void configure() throws Exception {
        // the request is sent as JSON text (see ActionRequest#toJson)
        from(ACTION_ENDPOINT).routeId("actionRoute").setExchangePattern(ExchangePattern.InOnly)
                .process(e -> e.getIn().setBody(ActionRequest.fromJson(e.getIn().getBody(String.class))))
                .bean(() -> this, "performAction");
    }

    @SneakyThrows
    void performAction(@Body ActionRequest actionRequest) {
        String actionKey = actionRequest.getActionKey();
        boolean sendMail = actionRequest.isSendMail();
        boolean sendSms = actionRequest.isSendSms();
        List<ActionParameter> parameters = actionRequest.getParameters();
        log.debug("performing action, action key {}, sendMail {}, parameters {}", actionKey, sendMail, parameters);

        var actionOptional = actions.stream().filter(action -> action.getKey().equals(actionKey)).findFirst();

        if (actionOptional.isPresent()) {
            var action = actionOptional.get();

            if (action.shouldNotRun(parameters)) {
                log.warn("action {} did not match requirements to run (check shouldNotRun method).", actionKey);
                return;
            }

            var result = action.run(parameters);
            log.debug("action result : {}", result);

            if (sendMail || StatusType.FAILURE.equals(result.getStatus())) {
                personalInfoService.getOptional().map(PersonalInfo::getOrganizationEmailAddress).ifPresent(mail -> {
                    String body = templateRenderer.renderDefaultText(OaTemplateType.ACTION,
                            Map.of("actionResult", result));

                    this.mailJobRepository.sendDelayedMail(List.of(mail), "Batch Action: " + actionKey, body, false,
                            List.of(), LocalDateTime.now());
                });
            }
            if (sendSms) {
                personalInfoService.getOptional().map(PersonalInfo::getOrganizationPhoneNumber).ifPresent(phone -> {
                    // todo getOrganizationPhoneNumber should be renamed to GSM or add a new field
                    String body = templateRenderer.renderDefaultText(OaTemplateType.SMS,
                            Map.of("actionResult", result, "actionKey", actionKey));
                    smsService.send(Sms.builder().phoneNumber(phone).message(body).build());
                });
            }

            if (actionRequest.isPersistResult()
                    && !ofNullable(result).map(ActionResult::getMessages).map(Collection::isEmpty).orElse(true)) {
                log.debug("save action");
                actionResultRepository.save(result);
            }
            action.afterRun();

        } else {
            log.warn("action not found");
        }

    }

    @PostConstruct
    public void postConstruct() {
        this.actions.forEach(action -> log.info("action {} registered.", action.getKey()));
    }
}
