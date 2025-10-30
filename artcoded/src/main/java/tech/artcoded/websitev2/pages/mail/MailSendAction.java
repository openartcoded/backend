package tech.artcoded.websitev2.pages.mail;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import tech.artcoded.websitev2.action.*;
import tech.artcoded.websitev2.notification.NotificationService;
import tech.artcoded.websitev2.upload.IFileUploadService;
import tech.artcoded.websitev2.utils.service.MailService;

@Slf4j
@Component
public class MailSendAction implements Action {
    public static final String ACTION_KEY = "MAIL_SEND_ACTION";
    private static final String MAIL_SENT = "MAIL_SENT";
    private final NotificationService notificationService;
    private final IFileUploadService fileUploadService;
    private final MailJobRepository repository;
    private final MailService mailService;

    public MailSendAction(NotificationService notificationService, IFileUploadService fileUploadService,
            MailService mailService, MailJobRepository repository) {
        this.notificationService = notificationService;
        this.fileUploadService = fileUploadService;
        this.repository = repository;
        this.mailService = mailService;
    }

    @Override
    public ActionResult run(List<ActionParameter> parameters) {
        Date date = new Date();
        var resultBuilder = this.actionResultBuilder(parameters).startedDate(date);

        List<String> messages = new ArrayList<>();

        try {
            var mailJobs = repository.findBySentIsFalseAndSendingDateIsBefore(date).stream()
                    .peek(mailJob -> messages.add("send mail for job id " + mailJob.getId()))
                    .filter(mailJob -> StringUtils.isNotEmpty(mailJob.getSubject())
                            && StringUtils.isNotEmpty(mailJob.getBody()) && !mailJob.getTo().isEmpty())
                    .toList();

            for (var mailJob : mailJobs) {
                messages.add("Sending email to %s with subject '%s'"
                        .formatted(mailJob.getTo().stream().collect(Collectors.joining(", ")), mailJob.getSubject()));
                var attachments = fileUploadService.findAll(mailJob.getUploadIds()).stream()
                        .map(u -> fileUploadService.toMockMultipartFile(u)).toList();
                mailService.sendMail(mailJob.getTo(), mailJob.getSubject(),
                        "<p>%s</p>".formatted(mailJob.getBody().replaceAll("(\r\n|\n)", "<br>")), mailJob.isBcc(),
                        attachments);
                repository.save(mailJob.toBuilder().updatedDate(new Date()).sent(true).build());
                messages.add("email for job id %s sent".formatted(mailJob.getId()));
                notificationService.sendEvent("Email for '%s' sent".formatted(mailJob.getSubject()), MAIL_SENT,
                        mailJob.getId());
            }

            return resultBuilder.finishedDate(new Date()).messages(messages).build();

        } catch (Exception e) {
            log.error("error while executing action", e);
            messages.add("error, see logs: %s".formatted(e.getMessage()));
            return resultBuilder.messages(messages).finishedDate(new Date()).status(StatusType.FAILURE).build();
        }
    }

    public static ActionMetadata defaultMetadata() {
        return ActionMetadata.builder().key(ACTION_KEY).title("Mail send Action")
                .description("An action to check periodically for mail job created").allowedParameters(List.of())
                .defaultCronValue("*/40 * * * * *").build();
    }

    @Override
    public ActionMetadata getMetadata() {
        return defaultMetadata();
    }

    @Override
    public String getKey() {
        return ACTION_KEY;
    }
}
