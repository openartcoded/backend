package tech.artcoded.websitev2.pages.task;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.time.DateUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;
import org.springframework.web.multipart.MultipartFile;

import freemarker.template.Configuration;
import freemarker.template.Template;
import tech.artcoded.websitev2.action.ActionService;
import tech.artcoded.websitev2.notification.NotificationService;
import tech.artcoded.websitev2.pages.personal.PersonalInfo;
import tech.artcoded.websitev2.pages.personal.PersonalInfoService;
import tech.artcoded.websitev2.rest.util.MockMultipartFile;
import tech.artcoded.websitev2.sms.Sms;
import tech.artcoded.websitev2.sms.SmsService;
import tech.artcoded.websitev2.utils.helper.DateHelper;
import tech.artcoded.websitev2.utils.service.MailService;

import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static java.util.Map.entry;

@Component
@Slf4j
public class ReminderTaskScheduler {
  private static final String REMINDER_TASK_NOTIFY = "REMINDER_TASK_NOTIFY";
  private final NotificationService notificationService;
  private final ReminderTaskService reminderTaskService;
  private final SmsService smsService;
  private final MailService mailService;
  private final PersonalInfoService personalInfoService;
  private final ActionService actionService;

  private final Configuration configuration;

  public ReminderTaskScheduler(NotificationService notificationService,
      ReminderTaskService reminderTaskService,
      MailService mailService,
      SmsService smsService,
      Configuration configuration,
      PersonalInfoService personalInfoService,
      ActionService actionService) {
    this.notificationService = notificationService;
    this.reminderTaskService = reminderTaskService;
    this.mailService = mailService;
    this.smsService = smsService;
    this.personalInfoService = personalInfoService;
    this.actionService = actionService;
    this.configuration = configuration;
  }

  @Scheduled(fixedDelay = 1000, initialDelay = 5000)
  public void checkRunTasks() {
    this.reminderTaskService.findByDisabledFalseAndNextDateBefore(new Date())
        .forEach(task -> {
          if (isNotEmpty(task.getActionKey())) {
            this.actionService.perform(task.getActionKey(),
                ofNullable(task.getActionParameters()).orElseGet(Collections::emptyList),
                task.isSendMail(), task.isSendSms(),

                task.isPersistResult());
          } else {
            if (task.isSendMail()) {
              personalInfoService.getOptional()
                  .ifPresent(pi -> {
                    List<MultipartFile> attachments = Collections.emptyList();
                    if (task.getCalendarDate() != null) {
                      var cd = task.getCalendarDate();

                      try {
                        Template template = configuration.getTemplate("make-ics.ftl");
                        String ics = FreeMarkerTemplateUtils.processTemplateIntoString(template,
                            Map.ofEntries(
                                entry("fullName", pi.getCeoFullName()),
                                entry("dtstamp", DateHelper.getICSDate(new Date())),
                                entry("dtstart", DateHelper.getICSDate(cd)),
                                entry("dtend", DateHelper.getICSDate(DateUtils.addHours(cd, 1))),
                                entry("title", task.getTitle()),
                                entry("description", task.getDescription()),
                                entry("companyName", pi.getOrganizationName()),
                                entry("email", pi.getOrganizationEmailAddress()),
                                entry("tzid", ZoneId.of("Europe/Paris")),
                                entry("loc", "HOME")));
                        attachments = List.of(MockMultipartFile.builder()
                            .name("invite.ics")
                            .contentType("text/calendar")
                            .originalFilename("invite.ics")
                            .bytes(ics.getBytes())
                            .build());
                      } catch (Throwable exc) {
                        log.error("could not write calendar", exc);
                        attachments = List.of();
                      }

                    }
                    mailService.sendMail(List.of(pi.getOrganizationEmailAddress()), task.getTitle(),
                        "<p>%s</p>".formatted(task.getDescription().replaceAll("(\r\n|\n)", "<br>")),
                        false, attachments);

                  });
            }
            if (task.isSendSms()) {
              personalInfoService.getOptional()
                  .map(PersonalInfo::getOrganizationPhoneNumber)
                  .ifPresent(phone -> {
                    smsService.send(Sms.builder().phoneNumber(phone).message(task.getDescription()).build());
                  });
            }
          }
          var customActionName = task.getCustomActionName();
          var title = isNotEmpty(customActionName) ? customActionName : task.getTitle();
          if (task.isInAppNotification()) {
            notificationService.sendEvent(title, REMINDER_TASK_NOTIFY, task.getId());
          } else {
            log.trace("Task: %s".formatted(title));
          }
          reminderTaskService.saveSync(task.toBuilder().lastExecutionDate(new Date()).build(), false);

        });
  }
}
