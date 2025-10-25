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
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
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

  public ReminderTask saveSync(ReminderTask reminderTask, boolean sendNotification) {
    String cronExpression = reminderTask.getCronExpression();
    Date specificDate = reminderTask.getSpecificDate();
    ReminderTask taskFromDb = ofNullable(reminderTask.getId()).flatMap(this::findById)
        .orElseGet(ReminderTask.builder()::build);

    if (cronExpression != null && !CronUtil.isValidCronExpression(cronExpression)) {
      throw new RuntimeException("Cron expression is not valid!");
    }

    Date nextDate = specificDate != null ? specificDate : getNextDateFromCronExpression(cronExpression, new Date());

    ReminderTask.ReminderTaskBuilder reminderTaskBuilder = taskFromDb.toBuilder().title(reminderTask.getTitle())
        .description(reminderTask.getDescription()).cronExpression(cronExpression)
        .disabled(reminderTask.isDisabled() || nextDate == null).persistResult(reminderTask.isPersistResult())
        .actionParameters(reminderTask.getActionParameters()).actionKey(reminderTask.getActionKey())
        .customActionName(reminderTask.getActionKey() != null ? reminderTask.getCustomActionName() : null)
        .lastExecutionDate(reminderTask.getLastExecutionDate())
        .inAppNotification(reminderTask.isInAppNotification()).sendMail(reminderTask.isSendMail())
        .sendSms(reminderTask.isSendSms()).specificDate(specificDate)
        .calendarDate(reminderTask.getCalendarDate())
        .nextDate(reminderTask.isDisabled() ? null : nextDate)
        .updatedDate(new Date());

    ReminderTask save = repository.save(reminderTaskBuilder.build());
    if (sendNotification) {
      var title = isNotEmpty(save.getCustomActionName()) ? save.getCustomActionName() : save.getTitle();
      this.notificationService.sendEvent("task '%s' saved or updated".formatted(title),
          REMINDER_TASK_ADD_OR_UPDATE, save.getId());
    }
    return save;
  }

  @Async
  public void save(ReminderTask reminderTask, boolean sendNotification) {
    saveSync(reminderTask, sendNotification);
  }

  public List<ReminderTask> findAll() {
    return repository.findAll();
  }

  public List<ReminderTask> findByDisabledTrueAndActionKeyIsNullAndUpdatedDateBefore(Date date) {
    return repository.findByDisabledTrueAndActionKeyIsNullAndUpdatedDateBefore(date);
  }

  public long countByDisabledTrueAndActionKeyIsNullAndUpdatedDateBefore(Date date) {
    return repository.countByDisabledTrueAndActionKeyIsNullAndUpdatedDateBefore(date);
  }

  public List<ReminderTask> findNext10Tasks() {
    return repository.findTop10ByDisabledFalseAndActionKeyIsNullOrderByNextDateAsc();
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
      var customActionName = reminderTask.getCustomActionName();
      repository.delete(reminderTask);
      this.notificationService.sendEvent(
          "task '%s' deleted"
              .formatted(isNotEmpty(customActionName) ? customActionName : reminderTask.getTitle()),
          REMINDER_TASK_DELETE, reminderTask.getId());
    });
  }

  public void deleteWithoutNotify(String id) {
    repository.deleteById(id);
  }

  public List<ReminderTask> findByOrderByNextDateDesc() {
    return repository.findByOrderByNextDateDesc();
  }

  public List<ReminderTask> findByOrderByNextDateAsc() {
    return repository.findByOrderByNextDateAsc();
  }
}
