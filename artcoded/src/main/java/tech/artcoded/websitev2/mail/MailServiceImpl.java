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
import org.springframework.web.multipart.MultipartFile;

import tech.artcoded.websitev2.utils.service.MailService;

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

  @Override
  @SneakyThrows
  @Async
  public void sendMail(String to, String subject, String htmlBody, boolean bcc, Supplier<List<File>> attachments) {
    var enrichAttachments = attachments.get();
    var fileNames = enrichAttachments.stream().map(a -> a.getName()).toList();
    var helper = makeMimeMessageHelper(to, subject, htmlBody, bcc, fileNames);

    enrichAttachments.forEach(a -> addFileAttachment(helper, a));

    emailSender.send(helper.getMimeMessage());
    log.info("mail sent");

  }

  @Override
  @SneakyThrows
  @Async
  public void sendMail(String to, String subject, String htmlBody, boolean bcc, List<MultipartFile> attachments) {
    var fileNames = attachments.stream().map(a -> a.getOriginalFilename()).toList();
    var helper = makeMimeMessageHelper(to, subject, htmlBody, bcc, fileNames);
    attachments.forEach(a -> addMultipartAttachment(helper, a));
    emailSender.send(helper.getMimeMessage());
    log.info("mail sent");
  }

  @SneakyThrows
  private MimeMessageHelper makeMimeMessageHelper(String to, String subject, String htmlBody, boolean bcc,
      List<String> fileNames) {
    var message = emailSender.createMimeMessage();
    var helper = new MimeMessageHelper(message, true, "UTF-8");
    var fromIA = new InternetAddress(from, from, "UTF-8");
    helper.setFrom(fromIA);
    helper.setTo(to);
    helper.setSubject(subject);
    Template t = configuration.getTemplate("email-template.ftl");

    String readyParsedTemplate = FreeMarkerTemplateUtils.processTemplateIntoString(t, Map.of(
        "subject", subject,
        "htmlBody", htmlBody,
        "attachments", fileNames));
    helper.setText(readyParsedTemplate, true);
    if (bcc) {
      helper.setBcc(fromIA);
    }
    return helper;
  }

  private void addFileAttachment(MimeMessageHelper helper, File a) {
    try {
      helper.addAttachment(a.getName(), a);
    } catch (MessagingException e) {
      log.error("error with attachment: ", e);
    }
  }

  private void addMultipartAttachment(MimeMessageHelper helper, MultipartFile a) {
    try {
      helper.addAttachment(a.getOriginalFilename(), a);
    } catch (MessagingException e) {
      log.error("error with attachment: ", e);
    }
  }

}
