package tech.artcoded.websitev2.pages.task;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Date;
import java.util.List;

public interface ReminderTaskRepository extends MongoRepository<ReminderTask, String> {
  List<ReminderTask> findByDisabledFalseAndNextDateBefore(Date date);

  List<ReminderTask> findByDisabledTrueAndActionKeyIsNullAndUpdatedDateBefore(Date date);

  List<ReminderTask> findByActionKeyIsNotNull();

  List<ReminderTask> findByOrderByNextDateDesc();

  List<ReminderTask> findByOrderByNextDateAsc();
}
