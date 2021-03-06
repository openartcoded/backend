package tech.artcoded.websitev2.action;

import freemarker.template.Configuration;
import freemarker.template.Template;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Body;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;
import tech.artcoded.websitev2.api.service.MailService;
import tech.artcoded.websitev2.pages.personal.PersonalInfo;
import tech.artcoded.websitev2.pages.personal.PersonalInfoService;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;

import static org.springframework.ui.freemarker.FreeMarkerTemplateUtils.processTemplateIntoString;
import static tech.artcoded.websitev2.action.ActionService.ACTION_ENDPOINT;
import static tech.artcoded.websitev2.api.func.CheckedSupplier.toSupplier;

@Component
@Slf4j
public class ActionRouteBuilder extends RouteBuilder {
  private final List<Action> actions;
  private final PersonalInfoService personalInfoService;
  private final MailService mailService;
  private final Configuration configuration;
  private final ActionResultRepository actionResultRepository;

  public ActionRouteBuilder(List<Action> actions,
                            PersonalInfoService personalInfoService,
                            MailService mailService,
                            Configuration configuration,
                            ActionResultRepository actionResultRepository) {
    this.actions = actions;
    this.personalInfoService = personalInfoService;
    this.mailService = mailService;
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
    List<ActionParameter> parameters = actionRequest.getParameters();
    log.debug("performing action, action key {}, sendMail {}, parameters {}", actionKey, sendMail, parameters);
    actions.stream().filter(action -> action.getKey().equals(actionKey)).findFirst().ifPresentOrElse(action -> {
      ActionResult result = action.run(parameters);
      log.debug("action result : {}", result);
      if (sendMail) {
        personalInfoService.getOptional().map(PersonalInfo::getOrganizationEmailAddress).ifPresent(mail -> {
          Template template = toSupplier(() -> configuration.getTemplate("action-template.ftl")).get();
          String body = toSupplier(() -> processTemplateIntoString(template, Map.of("actionResult", result))).get();
          mailService.sendMail(mail, "Batch Action: " + actionKey, body, false, MailService.emptyAttachment());
        });
      }
      if (actionRequest.isPersistResult()) {
        log.debug("save action");
        actionResultRepository.save(result);
      }
    }, () -> log.warn("action not found"));
  }

  @PostConstruct
  public void postConstruct() {
    this.actions.forEach(action -> log.info("action {} registered.", action.getKey()));
  }
}
