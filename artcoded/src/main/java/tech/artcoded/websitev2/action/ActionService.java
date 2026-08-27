package tech.artcoded.websitev2.action;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.ServiceStatus;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.pages.mail.MailJobRepository;
import tech.artcoded.websitev2.rest.util.CronUtil;
import tech.artcoded.websitev2.utils.helper.DateHelper;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class ActionService {
    public static final String ACTION_ENDPOINT = "jms:topic:action";
    private final ProducerTemplate producerTemplate;
    private final MailJobRepository mailJobRepository;
    private final ActionResultRepository actionResultRepository;
    private final List<Action> actions;
    private final CamelContext camelContext;

    @Value("${application.admin.email}")
    private String adminEmail;

    public ActionService(ProducerTemplate producerTemplate, ActionResultRepository actionResultRepository,
            CamelContext camelContext, MailJobRepository mailJobRepository, List<Action> actions) {
        this.producerTemplate = producerTemplate;
        this.actionResultRepository = actionResultRepository;
        this.actions = actions;
        this.camelContext = camelContext;
        this.mailJobRepository = mailJobRepository;
    }

    @PostConstruct
    @SneakyThrows
    public void startInternalTasks() {
        Thread.ofVirtual().start(() -> {
            while (!camelContext.getStatus().equals(ServiceStatus.Started)) {
                log.info("camel is not started yet, sleeping...");

                try {
                    Thread.sleep(Duration.ofSeconds(5));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            log.info("running internal action routine...");
            var internalActions = actions.stream().filter(a -> a.getDefaultActionRequest().isPresent()).toList();
            if (internalActions.isEmpty()) {
                log.info("no internal action to run. Stopping routine...");
                return;
            }

            for (var internalAction : internalActions) {

                ActionMetadata metadata = internalAction.getMetadata();
                log.info("Starting Internal action: {}", metadata.getKey());

                Thread.ofVirtual().start(() -> {
                    while (true) {
                        var nextDate = CronUtil.getNextDateFromCronExpression(metadata.getDefaultCronValue(),
                                new Date());
                        log.info("sleeping before running {} at {}", metadata.getKey(),
                                DateHelper.getDateToString(nextDate));
                        Duration sleepDuration = Duration.between(Instant.now(), nextDate.toInstant());
                        if (!sleepDuration.isNegative()) {
                            try {
                                Thread.sleep(sleepDuration.toMillis());
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                        log.info("running {}", metadata.getKey());
                        this.producerTemplate.sendBody(ACTION_ENDPOINT, internalAction.getDefaultActionRequest().get());
                    }

                });
            }
        });
    }

    @Async
    public void perform(String actionKey, List<ActionParameter> actionParameters, boolean sendMail, boolean sendSms,
            boolean isPersistResult) {

        var actionOp = this.actions.stream().filter(a -> Strings.CS.equals(actionKey, a.getKey())).findFirst();

        if (actionOp.isPresent()) {
            var action = actionOp.get();
            if (!action.getDefaultActionRequest().isEmpty()) {
                this.mailJobRepository.sendDelayedMail(List.of(adminEmail),
                        "WARNING: " + actionKey + " is now an internal action!",
                        """
                                The following action %s is now an internal action. It will not run! Instead, it runs automatically.<br>
                                Please check logs to make sure it ran, then disable it in the UI.<br>
                                """
                                .formatted(actionKey),
                        false, List.of(), LocalDateTime.now().plusMinutes(RandomUtils.secure().randomLong(1, 10)));
                return;
            } else {
                this.mailJobRepository.sendDelayedMail(List.of(adminEmail),
                        "WARNING: " + actionKey + " doesn't seem to exist!", """
                                The following action %s doesn't seem to exist anymore! please check the logs.
                                """.formatted(actionKey), false, List.of(),
                        LocalDateTime.now().plusMinutes(RandomUtils.secure().randomLong(1, 10)));
                return;
            }
        }

        ActionRequest actionRequest = ActionRequest.builder().parameters(actionParameters).actionKey(actionKey)
                .persistResult(isPersistResult).sendMail(sendMail).sendSms(sendSms).build();
        this.producerTemplate.sendBody(ACTION_ENDPOINT, actionRequest);
    }

    public Page<ActionResult> findActionResults(String actionKey, Pageable pageable) {
        return actionResultRepository.findByActionKeyOrderByCreatedDateDesc(actionKey, pageable);
    }

    protected List<ActionResult> findAll() {
        return actionResultRepository.findAll();
    }

    public List<ActionMetadata> getAllowedActions() {
        return this.actions.stream().filter(action -> action.getDefaultActionRequest().isEmpty()) // internal action
                                                                                                  // should not be
                                                                                                  // listed
                .map(Action::getMetadata).toList();
    }

    public void deleteByFinishedDateBefore(Date date) {
        actionResultRepository.deleteByFinishedDateBefore(date);
    }

    public long count() {
        return actionResultRepository.count();
    }

}
