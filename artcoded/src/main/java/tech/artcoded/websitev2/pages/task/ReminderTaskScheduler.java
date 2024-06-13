package tech.artcoded.websitev2.pages.task;

import lombok.extern.slf4j.Slf4j;
import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.TimeZoneRegistryFactory;
import net.fortuna.ical4j.model.component.VTimeZone;
import net.fortuna.ical4j.model.parameter.Cn;
import net.fortuna.ical4j.model.parameter.Role;
import net.fortuna.ical4j.model.property.Attendee;
import net.fortuna.ical4j.model.component.VEvent;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ibm.icu.util.TimeZone;

import tech.artcoded.websitev2.action.ActionService;
import tech.artcoded.websitev2.notification.NotificationService;
import tech.artcoded.websitev2.pages.personal.PersonalInfo;
import tech.artcoded.websitev2.pages.personal.PersonalInfoService;
import tech.artcoded.websitev2.sms.Sms;
import tech.artcoded.websitev2.sms.SmsService;
import tech.artcoded.websitev2.utils.helper.IdGenerators;
import tech.artcoded.websitev2.utils.service.MailService;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

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

  public ReminderTaskScheduler(NotificationService notificationService,
      ReminderTaskService reminderTaskService,
      MailService mailService,
      SmsService smsService,
      PersonalInfoService personalInfoService,
      ActionService actionService) {
    this.notificationService = notificationService;
    this.reminderTaskService = reminderTaskService;
    this.mailService = mailService;
    this.smsService = smsService;
    this.personalInfoService = personalInfoService;
    this.actionService = actionService;
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
                    var attachments = MailService.emptyAttachment();
                    if (task.getCalendarDate() != null) {
                      var cd = task.getCalendarDate();
                      attachments = () -> {
                        DateTime start = new DateTime(cd);
                        DateTime end = new DateTime(DateUtils.addHours(cd, 1));
                        TimeZoneRegistry registry = TimeZoneRegistryFactory.getInstance().createRegistry();
                        var timezone = registry.getTimeZone(java.util.TimeZone.getDefault().getID());
                        VTimeZone tz = timezone.getVTimeZone();

                        VEvent meeting = new VEvent(start, end, task.getTitle())
                            .withProperty(tz.getTimeZoneId())
                            .withProperty(
                                new Attendee(URI.create(pi.getOrganizationEmailAddress()))
                                    .withParameter(Role.REQ_PARTICIPANT)
                                    .withParameter(new Cn(pi.getCeoFullName()))
                                    .getFluentTarget())
                            .getFluentTarget();
                        var calendar = new Calendar()
                            .withProdId("-//%s//%s//EN".formatted(pi.getCeoFullName(), pi.getOrganizationName()))
                            .withDefaults()
                            .withComponent(meeting)
                            .getFluentTarget();

                        File temp = new File(FileUtils.getTempDirectory(), IdGenerators.get() + ".ics");
                        try (var fso = new FileOutputStream(temp)) {
                          CalendarOutputter out = new CalendarOutputter();
                          out.output(calendar, fso);
                          return List.of(temp);
                        } catch (Throwable exc) {
                          log.error("could not write calendar", exc);
                          return List.of();
                        }

                      };

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
