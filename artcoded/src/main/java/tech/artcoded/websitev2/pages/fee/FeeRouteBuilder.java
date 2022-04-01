package tech.artcoded.websitev2.pages.fee;

import lombok.extern.slf4j.Slf4j;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mail.ContentTypeResolver;
import org.apache.camel.component.mail.MailComponent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tech.artcoded.websitev2.camel.mail.Mail;
import tech.artcoded.websitev2.camel.mail.MailTransformer;
import tech.artcoded.websitev2.notification.NotificationService;

@Component
@Slf4j
public class FeeRouteBuilder extends RouteBuilder {

  private static final String NOTIFICATION_TYPE = "NEW_FEE";

  @Value("${application.camel.mail.port}")
  private String port;
  @Value("${application.camel.mail.username}")
  private String username;
  @Value("${application.camel.mail.host}")
  private String host;
  @Value("${application.camel.mail.password}")
  private String password;
  @Value("${application.camel.mail.protocol}")
  private String protocol;

  private final FeeService feeService;

  public FeeRouteBuilder(FeeService feeService) {
    this.feeService = feeService;
  }

  @Override
  public void configure() throws Exception {

    ContentTypeResolver resolver = MailTransformer.CONTENT_TYPE_RESOLVER;
    MailComponent mailComponent = getContext().getComponent("imaps", MailComponent.class);
    mailComponent.setContentTypeResolver(resolver);

    fromF("%s://%s:%s?password=%s&username=%s&delete=false&delay=30000", protocol, host, port, password, username)
            .routeId("feeMailRoute")
            .transform()
            .exchange(MailTransformer::transform)
            .transform()
            .body(Mail.class, this::toFee)
            .setHeader(NotificationService.HEADER_TYPE, constant(NOTIFICATION_TYPE))
            .setHeader(NotificationService.HEADER_TITLE, simple("${body.subject}"))
            .setBody(constant(null))
            .to(ExchangePattern.InOnly, NotificationService.NOTIFICATION_ENDPOINT);
  }

  private Fee toFee(Mail mail) {
    return this.feeService.save(
            mail.getSubject(), mail.getBody(), mail.getDate(), mail.getAttachments());
  }
}
