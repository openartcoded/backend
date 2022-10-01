package tech.artcoded.websitev2.utils.service;

import java.io.File;
import java.util.List;
import java.util.function.Supplier;

public interface MailService {
  void sendMail(String to, String subject, String htmlBody, boolean bcc, Supplier<List<File>> attachments);

  static Supplier<List<File>> emptyAttachment() {
    return List::of;
  }
}
