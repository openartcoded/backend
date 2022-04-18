package tech.artcoded.websitev2.mail;

import freemarker.template.Configuration;
import freemarker.template.Template;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;
import tech.artcoded.websitev2.api.service.MailService;

import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Service
@Slf4j
public class MailServiceImpl implements MailService {

  private final JavaMailSender emailSender;
  private final Configuration configuration;

  @Value("${spring.mail.username}")
  private String from;

  @Inject
  public MailServiceImpl(JavaMailSender emailSender, Configuration configuration) {
    this.emailSender = emailSender;
    this.configuration = configuration;
  }

  private void addAttachment(MimeMessageHelper helper, File a) {
    try {
      helper.addAttachment(a.getName(), a);
    } catch (MessagingException e) {
      log.error("error with attachment: ", e);
    }
  }

  @Override
  @SneakyThrows
  @Async
  public void sendMail(String to, String subject, String htmlBody, boolean bcc, Supplier<List<File>> attachments) {
    var message = emailSender.createMimeMessage();
    var enrichAttachments = attachments.get();

    var helper = new MimeMessageHelper(message, true, "UTF-8");
    var fromIA = new InternetAddress(from, from, "UTF-8");
    helper.setFrom(fromIA);
    helper.setTo(to);
    helper.setSubject(subject);

    enrichAttachments.forEach(a -> addAttachment(helper, a));

    Template t = configuration.getTemplate("email-template.ftl");

    String readyParsedTemplate = FreeMarkerTemplateUtils.processTemplateIntoString(t, Map.of(
      "subject", subject,
      "htmlBody", htmlBody,
      "attachments", enrichAttachments.stream()
        .map(File::getName)
        .toList()
    ));


    helper.setText(readyParsedTemplate, true);

    if (bcc) {
      helper.setBcc(fromIA);
    }

    emailSender.send(helper.getMimeMessage());
    log.info("mail sent");

  }
}
