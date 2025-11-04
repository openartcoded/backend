package tech.artcoded.websitev2.pages.mail;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import tech.artcoded.websitev2.utils.helper.DateHelper;

public interface MailJobRepository extends MongoRepository<MailJob, String> {
    List<MailJob> findBySentIsFalseAndSendingDateIsBefore(Date sendingDate);

    Page<MailJob> findByOrderBySendingDateDesc(Pageable pageable);

    default void sendDelayedMail(List<String> to, String subject, String htmlBody, boolean bcc,
            List<String> attachmentIds, LocalDateTime sendingDate) {
        this.save(MailJob.builder().sendingDate(DateHelper.toDate(sendingDate)).subject(subject).body(htmlBody)
                .sent(false).to(to).bcc(bcc).uploadIds(attachmentIds).build());
    }

}
