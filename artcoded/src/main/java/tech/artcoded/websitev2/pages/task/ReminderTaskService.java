package tech.artcoded.websitev2.pages.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tech.artcoded.websitev2.notification.NotificationService;
import tech.artcoded.websitev2.rest.util.CronUtil;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static java.util.Optional.ofNullable;
import static tech.artcoded.websitev2.rest.util.CronUtil.getNextDateFromCronExpression;

@Service
@Slf4j
public class ReminderTaskService {
  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
  protected static final String REMINDER_TASK_ADD_OR_UPDATE = "REMINDER_TASK_ADD_OR_UPDATE";
  protected static final String REMINDER_TASK_DELETE = "REMINDER_TASK_DELETE";
  private final ReminderTaskRepository repository;
  private final NotificationService notificationService;

  public ReminderTaskService(ReminderTaskRepository repository, NotificationService notificationService) {
    this.repository = repository;
    this.notificationService = notificationService;
  }

  void saveSync(ReminderTask reminderTask, boolean sendNotification) {
    String cronExpression = reminderTask.getCronExpression();
    Date specificDate = reminderTask.getSpecificDate();
    ReminderTask taskFromDb = ofNullable(reminderTask.getId()).flatMap(this::findById)
        .orElseGet(ReminderTask.builder()::build);
    Date previousDate = taskFromDb.getNextDate();

    if (cronExpression != null && !CronUtil.isValidCronExpression(cronExpression)) {
      throw new RuntimeException("Cron expression is not valid!");
    }

    Date nextDate = specificDate != null ? specificDate : getNextDateFromCronExpression(cronExpression, new Date());

    ReminderTask.ReminderTaskBuilder reminderTaskBuilder = taskFromDb.toBuilder()
        .title(reminderTask.getTitle())
        .description(reminderTask.getDescription())
        .cronExpression(cronExpression)
        .disabled(reminderTask.isDisabled())
        .persistResult(reminderTask.isPersistResult())
        .actionParameters(reminderTask.getActionParameters())
        .actionKey(reminderTask.getActionKey())
        .customActionName(reminderTask.getActionKey() != null ? reminderTask.getCustomActionName() : null)
        .lastExecutionDate(reminderTask.getLastExecutionDate())
        .inAppNotification(reminderTask.isInAppNotification())
        .sendMail(reminderTask.isSendMail())
        .specificDate(specificDate)
        .nextDate(reminderTask.isDisabled() ? null : nextDate)
        .updatedDate(new Date());

    if (specificDate != null && reminderTask.getLastExecutionDate() != null && DATE_FORMAT.format(nextDate)
        .equals(ofNullable(previousDate).map(DATE_FORMAT::format)
            .orElse(null))) {
      log.info("action with a specific date set to {} already executed on {}. Disabling task...", specificDate,
          reminderTask.getLastExecutionDate());
      reminderTaskBuilder = reminderTaskBuilder.disabled(true).nextDate(null).lastExecutionDate(null);
    }

    ReminderTask save = repository.save(reminderTaskBuilder.build());
    if (sendNotification) {
      this.notificationService.sendEvent("task '%s' saved or updated".formatted(save.getTitle()),
          REMINDER_TASK_ADD_OR_UPDATE, save.getId());
    }
  }

  @Async
  public void save(ReminderTask reminderTask, boolean sendNotification) {
    saveSync(reminderTask, sendNotification);
  }

  public List<ReminderTask> findAll() {
    return repository.findAll();
  }

  public List<ReminderTask> findByActionKeyNotNull() {
    return repository.findByActionKeyIsNotNull();
  }

  public List<ReminderTask> findByDisabledFalseAndNextDateBefore(Date date) {
    return repository.findByDisabledFalseAndNextDateBefore(date);
  }

  public Optional<ReminderTask> findById(String id) {
    return repository.findById(id);
  }

  @Async
  public void delete(String id) {
    repository.findById(id).ifPresent(reminderTask -> {
      repository.delete(reminderTask);
      this.notificationService.sendEvent("task '%s' deleted".formatted(reminderTask.getTitle()), REMINDER_TASK_DELETE,
          reminderTask.getId());
    });
  }

  public List<ReminderTask> findByOrderByNextDateDesc() {
    return repository.findByOrderByNextDateDesc();
  }

  public List<ReminderTask> findByOrderByNextDateAsc() {
    return repository.findByOrderByNextDateAsc();
  }
}
