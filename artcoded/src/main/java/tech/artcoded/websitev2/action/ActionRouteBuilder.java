package tech.artcoded.websitev2.action;

import freemarker.template.Configuration;
import freemarker.template.Template;
import jakarta.annotation.PostConstruct;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Body;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;
import tech.artcoded.websitev2.pages.personal.PersonalInfo;
import tech.artcoded.websitev2.pages.personal.PersonalInfoService;
import tech.artcoded.websitev2.sms.Sms;
import tech.artcoded.websitev2.sms.SmsService;
import tech.artcoded.websitev2.utils.service.MailService;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static java.util.Optional.ofNullable;
import static org.springframework.ui.freemarker.FreeMarkerTemplateUtils.processTemplateIntoString;
import static tech.artcoded.websitev2.action.ActionService.ACTION_ENDPOINT;
import static tech.artcoded.websitev2.utils.func.CheckedSupplier.toSupplier;

@Component
@Slf4j
public class ActionRouteBuilder extends RouteBuilder {
  private final List<Action> actions;
  private final PersonalInfoService personalInfoService;
  private final MailService mailService;
  private final SmsService smsService;
  private final Configuration configuration;
  private final ActionResultRepository actionResultRepository;

  public ActionRouteBuilder(List<Action> actions,
      PersonalInfoService personalInfoService,
      MailService mailService,
      SmsService smsService,
      Configuration configuration,
      ActionResultRepository actionResultRepository) {
    this.actions = actions;
    this.personalInfoService = personalInfoService;
    this.mailService = mailService;
    this.smsService = smsService;
    this.configuration = configuration;
    this.actionResultRepository = actionResultRepository;
  }

  @Override
  public void configure() throws Exception {
    from(ACTION_ENDPOINT)
        .routeId("actionRoute")
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
      var result = action.run(parameters);
      log.debug("action result : {}", result);

      if (action.noOp()) {
        log.info("action is no op.");
        return;
      }

      if (sendMail) {
        personalInfoService.getOptional().map(PersonalInfo::getOrganizationEmailAddress).ifPresent(mail -> {
          Template template = toSupplier(() -> configuration.getTemplate("action-template.ftl")).get();
          String body = toSupplier(() -> processTemplateIntoString(template, Map.of("actionResult", result))).get();
          mailService.sendMail(List.of(mail), "Batch Action: " + actionKey, body, false, MailService.emptyAttachment());
        });
      }
      if (sendSms) {
        personalInfoService.getOptional().map(PersonalInfo::getOrganizationPhoneNumber).ifPresent(phone -> {
          // todo getOrganizationPhoneNumber should be renamed to GSM or add a new field
          Template template = toSupplier(() -> configuration.getTemplate("action-sms-template.ftl")).get();
          String body = toSupplier(
              () -> processTemplateIntoString(template, Map.of("actionResult", result, "actionKey", actionKey))).get();
          smsService.send(Sms.builder().phoneNumber(phone).message(body).build());
        });
      }

      if (actionRequest.isPersistResult()
          && !ofNullable(result).map(ActionResult::getMessages).map(Collection::isEmpty).orElse(true)) {
        log.debug("save action");
        actionResultRepository.save(result);
      }

    } else {
      log.warn("action not found");
    }

  }

  @PostConstruct
  public void postConstruct() {
    this.actions.forEach(action -> log.info("action {} registered.", action.getKey()));
  }
}
