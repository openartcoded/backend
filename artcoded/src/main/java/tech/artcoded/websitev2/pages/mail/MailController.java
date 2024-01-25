package tech.artcoded.websitev2.pages.mail;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
  private final MailJobRepository jobRepository;

  @Inject
  public MailController(FileUploadService uploadService,
      MailJobRepository jobRepository) {
    this.jobRepository = jobRepository;
  }

  @GetMapping
  public Page<MailJob> findAll(Pageable pageable) {
    return this.jobRepository.findAll(pageable);
  }

  @PostMapping("/send")
  public ResponseEntity<Void> sendMail(@RequestBody MailRequest mailRequest) {
    jobRepository.save(
        MailJob.builder()
            .sendingDate(Optional.ofNullable(mailRequest.getSendingDate())
                .orElseGet(Date::new))
            .subject(mailRequest.getSubject())
            .body(mailRequest.getBody())
            .uploadIds(mailRequest.getUploadIds())
            .to(mailRequest.getTo())
            .bcc(mailRequest.isBcc())
            .sent(false)
            .build());

    return ResponseEntity.ok().build();
  }
}
