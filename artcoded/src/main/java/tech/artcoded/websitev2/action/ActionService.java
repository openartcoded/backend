package tech.artcoded.websitev2.action;

import lombok.extern.slf4j.Slf4j;
import org.apache.camel.ProducerTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class ActionService {
  public static final String ACTION_ENDPOINT = "jms:topic:action";
  private final ProducerTemplate producerTemplate;
  private final ActionResultRepository actionResultRepository;
  private final List<Action> actions;

  public ActionService(ProducerTemplate producerTemplate, ActionResultRepository actionResultRepository, List<Action> actions) {
    this.producerTemplate = producerTemplate;
    this.actionResultRepository = actionResultRepository;
    this.actions = actions;
  }

  @Async
  public void perform(String actionKey, List<ActionParameter> actionParameters, boolean sendMail, boolean isPersistResult) {
    ActionRequest actionRequest = ActionRequest.builder().parameters(actionParameters).actionKey(actionKey)
                                               .persistResult(isPersistResult)
                                               .sendMail(sendMail).build();
    this.producerTemplate.sendBody(ACTION_ENDPOINT, actionRequest);
  }

  public Page<ActionResult> findActionResults(String actionKey, Pageable pageable) {
    return actionResultRepository.findByActionKeyOrderByCreatedDateDesc(actionKey, pageable);
  }

  public List<ActionMetadata> getAllowedActions() {
    return this.actions.stream().map(Action::getMetadata).toList();
  }

}
