package tech.artcoded.websitev2.pages.mail;

import java.util.Date;
import java.util.Optional;
import javax.inject.Inject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tech.artcoded.websitev2.upload.IFileUploadService;

@RestController
@RequestMapping("/api/mail")
public class MailController {
    private final MailJobRepository jobRepository;

    @Inject
    public MailController(IFileUploadService uploadService, MailJobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @GetMapping("/find-all")
    public Page<MailJob> findAll(Pageable pageable) {
        return this.jobRepository.findByOrderBySendingDateDesc(pageable);
    }

    @DeleteMapping("/delete")
    public void delete(@RequestParam("id") String id) {
        this.jobRepository.findById(id).filter(m -> !m.isSent())
                .ifPresent(mail -> this.jobRepository.deleteById(mail.getId()));
    }

    @PostMapping("/update")
    public ResponseEntity<MailJob> update(@RequestBody MailJob mailJob) {
        return Optional.ofNullable(mailJob.getId()).flatMap(jobRepository::findById).filter(m -> !m.isSent())
                .map(m -> m.toBuilder().subject(mailJob.getSubject()).body(mailJob.getBody()).updatedDate(new Date())
                        .to(mailJob.getTo()).build())
                .map(jobRepository::save).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.status(400).build());
    }

    @PostMapping("/send")
    public ResponseEntity<Void> sendMail(@RequestBody MailRequest mailRequest) {
        jobRepository.save(MailJob.builder()
                .sendingDate(Optional.ofNullable(mailRequest.getSendingDate()).orElseGet(Date::new))
                .subject(mailRequest.getSubject()).body(mailRequest.getBody()).uploadIds(mailRequest.getUploadIds())
                .to(mailRequest.getTo()).bcc(mailRequest.isBcc()).sent(false).build());

        return ResponseEntity.ok().build();
    }
}
