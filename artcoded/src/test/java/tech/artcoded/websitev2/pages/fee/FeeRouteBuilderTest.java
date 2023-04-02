package tech.artcoded.websitev2.pages.fee;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import lombok.SneakyThrows;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.MimeMessageHelper;
import tech.artcoded.websitev2.pages.fee.camel.FeeConfig;
import tech.artcoded.websitev2.pages.fee.camel.FeeRouteBuilder;

import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FeeRouteBuilderTest extends CamelTestSupport {

  @Mock
  private FeeService feeService;

  @RegisterExtension
  static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP_IMAP);

  @BeforeEach
  public void setup() {
    greenMail.setUser("noreply@example.com", "noreply@example.com", "noreply");
    greenMail.setUser("expense@example.com", "expense@example.com", "expense");
  }

  @Test
  @DisplayName("Send XLSX")
  public void testSendXlsx() throws Exception {
    // As the test passes, it seems the bug is related to either roundcube, or the
    // dev config not using ssl

    Transport.send(makeMessage("noreply@example.com"));

    getMockEndpoint("mock:result").expectedHeaderReceived("From", "noreply@example.com");
    MockEndpoint.assertIsSatisfied(context);
  }

  @Test
  @DisplayName("Send Non allowed email")
  public void testNonAllowedEmail() throws Exception {
    Transport.send(makeMessage("noreply@example.com"));
    Transport.send(makeMessage("noreply2@example.com"));
    Transport.send(makeMessage("hack@example.com"));
    Transport.send(makeMessage("hack3@example.com"));
    Transport.send(makeMessage("hack3d@example.com"));
    Transport.send(makeMessage("noreply@example.com"));

    MockEndpoint mockEndpoint = context.getEndpoint("mock:result", MockEndpoint.class);
    mockEndpoint.expectedHeaderValuesReceivedInAnyOrder("From", List.of("noreply@example.com", "noreply2@example.com"));
    mockEndpoint
        .expectedMessagesMatches(exchange -> {
          var header = exchange.getIn().getHeader("From", String.class);
          return !List.of("hack@example.com", "hack3d@example.com", "hack3@example.com").contains(header);
        });
    mockEndpoint.expectedMessageCount(3);
    MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);

  }

  @SneakyThrows
  MimeMessage makeMessage(String from) {
    Session smtpSession = greenMail.getSmtp().createSession();

    var msg = new MimeMessage(smtpSession);

    var helper = new MimeMessageHelper(msg, true, "UTF-8");

    helper.setFrom(new InternetAddress(from));
    helper.setTo(new InternetAddress("expense@example.com"));
    helper.setSubject("test");
    helper.setText("rest");
    helper.addAttachment("informative-summary-dossier-Q3-2021-52cc6d66-9a15-4709-8868-b7b819872ea8.xlsx",
        new ClassPathResource("informative-summary-dossier-Q3-2021-52cc6d66-9a15-4709-8868-b7b819872ea8.xlsx"));
    return helper.getMimeMessage();
  }

  @SneakyThrows
  @Override
  protected RoutesBuilder createRouteBuilder() {
    Mockito.when(feeService.save(anyString(), anyString(), Mockito.any(Date.class), Mockito.anyList())).thenReturn(
        new Fee());
    FeeConfig feeConfig = new FeeConfig();
    feeConfig.setFroms(List.of("noreply@example.com", "noreply2@example.com"));

    context.getRegistry().bind("searchTerm", feeConfig.searchTerm());
    var routeBuilder = new FeeRouteBuilder(feeService);
    routeBuilder.setDelay(10);
    routeBuilder.setDestination("mock:result");
    routeBuilder.setHost("localhost");
    routeBuilder.setPort(3143);
    routeBuilder.setUsername("expense@example.com");
    routeBuilder.setPassword("expense");
    routeBuilder.setProtocol("imap");
    return routeBuilder;
  }

}
