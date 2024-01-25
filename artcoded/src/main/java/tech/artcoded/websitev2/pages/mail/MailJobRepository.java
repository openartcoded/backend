package tech.artcoded.websitev2.pages.mail;

import java.util.Date;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MailJobRepository extends MongoRepository<MailJob, String> {
  List<MailJob> findBySentIsFalseAndSendingDateIsBefore(Date sendingDate);

  Page<MailJob> findByOrderBySendingDateDesc(Pageable pageable);
}
