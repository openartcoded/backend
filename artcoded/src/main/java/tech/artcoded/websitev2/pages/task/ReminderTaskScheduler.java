package tech.artcoded.websitev2.pages.task;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tech.artcoded.websitev2.action.ActionService;
import tech.artcoded.websitev2.notification.NotificationService;
import tech.artcoded.websitev2.pages.personal.PersonalInfoService;
import tech.artcoded.websitev2.utils.service.MailService;

import java.util.Collections;
import java.util.Date;

import static java.util.Optional.ofNullable;

@Component
@Slf4j
public class ReminderTaskScheduler {
  private static final String REMINDER_TASK_NOTIFY = "REMINDER_TASK_NOTIFY";
  private final NotificationService notificationService;
  private final ReminderTaskService reminderTaskService;
  private final MailService mailService;
  private final PersonalInfoService personalInfoService;
  private final ActionService actionService;

  public ReminderTaskScheduler(NotificationService notificationService,
                               ReminderTaskService reminderTaskService,
                               MailService mailService,
                               PersonalInfoService personalInfoService,
                               ActionService actionService) {
    this.notificationService = notificationService;
    this.reminderTaskService = reminderTaskService;
    this.mailService = mailService;
    this.personalInfoService = personalInfoService;
    this.actionService = actionService;
  }

  @Scheduled(fixedDelay = 1000,
    initialDelay = 5000)
  public void checkRunTasks() {
    this.reminderTaskService.findByDisabledFalseAndNextDateBefore(new Date())
      .forEach(task -> {
        if (StringUtils.isNotEmpty(task.getActionKey())) {
          this.actionService.perform(task.getActionKey(),
            ofNullable(task.getActionParameters()).orElseGet(Collections::emptyList),
            task.isSendMail(),
            task.isPersistResult());
        } else {
          if (task.isSendMail()) {
            personalInfoService.getOptional()
              .ifPresent(pi -> mailService.sendMail(pi.getOrganizationEmailAddress(), task.getTitle(),
                "<p>%s</p>".formatted(task.getDescription().replaceAll("(\r\n|\n)", "<br>")),
                false, MailService.emptyAttachment()));
          }
        }
        if (task.isInAppNotification()) {
          notificationService.sendEvent(task.getTitle(), REMINDER_TASK_NOTIFY, task.getId());
        } else {
          log.trace("Task: %s".formatted(task.getTitle()));
        }
        reminderTaskService.save(task.toBuilder().lastExecutionDate(new Date()).build(), false);
      });
  }
}
