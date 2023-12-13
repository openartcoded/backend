package tech.artcoded.websitev2.pages.mail;

import java.util.Date;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MailJobRepository extends MongoRepository<MailJob, String> {
  List<MailJob> findBySentIsFalseAndSendingDateIsBefore(Date sendingDate);
}
