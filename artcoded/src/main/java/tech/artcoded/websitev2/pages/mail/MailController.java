package tech.artcoded.websitev2.pages.mail;

import java.util.Date;
import java.util.Optional;
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
import tech.artcoded.websitev2.utils.helper.DateHelper;

@RestController
@RequestMapping("/api/mail")
public class MailController {
    private final MailJobRepository mailJobRepository;

    public MailController(IFileUploadService uploadService, MailJobRepository jobRepository) {
        this.mailJobRepository = jobRepository;
    }

    @GetMapping("/find-all")
    public Page<MailJob> findAll(Pageable pageable) {
        return this.mailJobRepository.findByOrderBySendingDateDesc(pageable);
    }

    @DeleteMapping("/delete")
    public void delete(@RequestParam("id") String id) {
        this.mailJobRepository.findById(id).filter(m -> !m.isSent())
                .ifPresent(mail -> this.mailJobRepository.deleteById(mail.getId()));
    }

    @PostMapping("/update")
    public ResponseEntity<MailJob> update(@RequestBody MailJob mailJob) {
        return Optional.ofNullable(mailJob.getId()).flatMap(mailJobRepository::findById).filter(m -> !m.isSent())
                .map(m -> m.toBuilder().subject(mailJob.getSubject()).body(mailJob.getBody()).updatedDate(new Date())
                        .to(mailJob.getTo()).build())
                .map(mailJobRepository::save).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(400).build());
    }

    @PostMapping("/send")
    public ResponseEntity<Void> sendMail(@RequestBody MailRequest mailRequest) {
        mailJobRepository.sendDelayedMail(mailRequest.getTo(), mailRequest.getSubject(), mailRequest.getBody(),
                mailRequest.isBcc(), mailRequest.getUploadIds(),
                DateHelper.toLocalDateTime(Optional.ofNullable(mailRequest.getSendingDate()).orElseGet(Date::new)));
        return ResponseEntity.ok().build();
    }
}
