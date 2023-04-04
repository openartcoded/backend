package tech.artcoded.websitev2.utils.service;

import java.io.File;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.web.multipart.MultipartFile;

public interface MailService {
  void sendMail(List<String> to, String subject, String htmlBody, boolean bcc, Supplier<List<File>> attachments);

  void sendMail(List<String> to, String subject, String htmlBody, boolean bcc, List<MultipartFile> attachments);

  static Supplier<List<File>> emptyAttachment() {
    return List::of;
  }
}
