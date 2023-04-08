package tech.artcoded.websitev2.pages.mail;

import java.util.List;

import javax.inject.Inject;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import tech.artcoded.websitev2.upload.FileUploadService;
import tech.artcoded.websitev2.utils.service.MailService;

@RestController
@RequestMapping("/api/mail")
public class MailController {
  private final FileUploadService uploadService;
  private final MailService mailService;

  @Inject
  public MailController(FileUploadService uploadService, MailService mailService) {
    this.uploadService = uploadService;
    this.mailService = mailService;
  }

  @PostMapping("/send")
  public ResponseEntity<Void> sendMail(@RequestBody MailRequest mailRequest) {
    List<MultipartFile> attachments = uploadService.findAll(mailRequest.getUploadIds())
        .stream()
        .map(u -> uploadService.toMockMultipartFile(u)).toList();
    mailService.sendMail(mailRequest.getTo(), mailRequest.getSubject(),
        "<p>%s</p>".formatted(mailRequest.getBody().replaceAll("(\r\n|\n)", "<br>")), mailRequest.isBcc(), attachments);

    return ResponseEntity.ok().build();
  }

}