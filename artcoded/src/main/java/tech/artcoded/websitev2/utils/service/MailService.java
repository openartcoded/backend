package tech.artcoded.websitev2.utils.service;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.web.multipart.MultipartFile;

public interface MailService {
    void sendMail(Collection<String> to, String subject, String htmlBody, boolean bcc,
            Supplier<List<File>> attachments);

    void sendMail(Collection<String> to, String subject, String htmlBody, boolean bcc, List<MultipartFile> attachments);

    static Supplier<List<File>> emptyAttachment() {
        return List::of;
    }

}
