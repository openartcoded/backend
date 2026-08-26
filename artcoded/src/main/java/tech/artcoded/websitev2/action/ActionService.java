package tech.artcoded.websitev2.action;

import org.apache.camel.ProducerTemplate;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import freemarker.template.utility.DateUtil;
import jakarta.annotation.PostConstruct;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.rest.util.CronUtil;
import tech.artcoded.websitev2.utils.helper.DateHelper;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class ActionService {
    public static final String ACTION_ENDPOINT = "jms:topic:action";
    private final ProducerTemplate producerTemplate;
    private final ActionResultRepository actionResultRepository;
    private final List<Action> actions;

    public ActionService(ProducerTemplate producerTemplate, ActionResultRepository actionResultRepository,
            List<Action> actions) {
        this.producerTemplate = producerTemplate;
        this.actionResultRepository = actionResultRepository;
        this.actions = actions;
    }

    @PostConstruct
    @SneakyThrows
    public void startInternalTasks() {
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
                    log.info("running {}", metadata.getKey());
                    this.producerTemplate.sendBody(ACTION_ENDPOINT, internalAction.getDefaultActionRequest());
                    var nextDate = CronUtil.getNextDateFromCronExpression(metadata.getDefaultCronValue(), new Date());
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
                }

            });
        }
    }

    @Async
    public void perform(String actionKey, List<ActionParameter> actionParameters, boolean sendMail, boolean sendSms,
            boolean isPersistResult) {
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
