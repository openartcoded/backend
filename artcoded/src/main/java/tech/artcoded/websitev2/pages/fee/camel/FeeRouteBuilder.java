package tech.artcoded.websitev2.pages.fee.camel;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mail.ContentTypeResolver;
import org.apache.camel.component.mail.MailComponent;
import org.apache.camel.component.mail.MailConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tech.artcoded.websitev2.camel.mail.Mail;
import tech.artcoded.websitev2.camel.mail.MailTransformer;
import tech.artcoded.websitev2.notification.NotificationService;
import tech.artcoded.websitev2.pages.fee.Fee;
import tech.artcoded.websitev2.pages.fee.FeeService;

import static org.apache.camel.ExchangePattern.InOnly;

@Component
@Slf4j
public class FeeRouteBuilder extends RouteBuilder {

  private static final String NOTIFICATION_TYPE = "NEW_FEE";

  @Value("${application.camel.mail.port}")
  @Setter
  private int port;
  @Value("${application.camel.mail.username}")
  @Setter
  private String username;
  @Value("${application.camel.mail.host}")
  @Setter
  private String host;
  @Value("${application.camel.mail.password}")
  @Setter
  private String password;
  @Value("${application.camel.mail.skipFailedMessage}")
  @Setter
  private boolean skipFailedMessage;
  @Value("${application.camel.mail.protocol}")
  @Setter
  private String protocol;
  @Value("${application.camel.mail.delay}")
  @Setter
  private int delay;
  @Value("${application.camel.mail.debugMode}")
  @Setter
  private boolean debugMode;

  @Setter
  private String destination = NotificationService.NOTIFICATION_ENDPOINT;

  private final FeeService feeService;

  public FeeRouteBuilder(FeeService feeService) {
    this.feeService = feeService;
  }

  @Override
  public void configure() throws Exception {
    setupComponents();

    fromF("%s://%s:%s?delay=%s&searchTerm=#searchTerm", protocol, host, port, delay)
      .routeId("feeMailRoute")
      .transform()
      .exchange(MailTransformer::transform)
      .transform()
      .body(Mail.class, this::toFee)
      .setHeader(NotificationService.HEADER_TYPE, constant(NOTIFICATION_TYPE))
      .setHeader(NotificationService.HEADER_TITLE, simple("${body.subject}"))
      .setBody(constant(null))
      .to(InOnly, destination);
  }

  private void setupComponents() {
    MailConfiguration configuration = new MailConfiguration();
    configuration.setUsername(username);
    configuration.setPassword(password);
    configuration.setDelete(false);
    configuration.setDebugMode(debugMode);

    configuration.setSkipFailedMessage(skipFailedMessage);
    ContentTypeResolver resolver = MailTransformer.CONTENT_TYPE_RESOLVER;

    MailComponent imapsComponent = getContext().getComponent("imaps", MailComponent.class);
    MailComponent imapComponent = getContext().getComponent("imap", MailComponent.class);

    imapsComponent.setContentTypeResolver(resolver);
    imapComponent.setContentTypeResolver(resolver);

    imapComponent.setConfiguration(configuration);
    imapsComponent.setConfiguration(configuration);
  }

  private Fee toFee(Mail mail) {
    return this.feeService.save(
      mail.getSubject(), mail.getBody(), mail.getDate(), mail.getAttachments());
  }
}
