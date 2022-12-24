package tech.artcoded.websitev2.actuator;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Service;
import org.springframework.context.ConfigurableApplicationContext;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.action.Action;
import tech.artcoded.websitev2.action.ActionMetadata;
import tech.artcoded.websitev2.action.ActionParameter;
import tech.artcoded.websitev2.action.ActionParameterType;
import tech.artcoded.websitev2.action.ActionResult;
import tech.artcoded.websitev2.action.StatusType;

@Service
@Slf4j
public class RestartAction implements Action, ApplicationContextAware {
  public static final String ACTION_KEY = "RESTART_ACTION";
  public static final String PARAMETER_DELAY = "PARAMETER_DELAY";
  public static final long PARAMETER_DELAY_DEFAULT = 30;

  @Setter
  @Getter
  private ApplicationContext applicationContext;

  @Override
  public ActionResult run(List<ActionParameter> parameters) {
    var resultBuilder = this.actionResultBuilder(parameters);
    List<String> messages = new ArrayList<>();
    try {
      messages.add("starting scheduled restart...");
      Long delay = parameters.stream()
          .filter(p -> PARAMETER_DELAY.equals(p.getKey()))
          .filter(p -> StringUtils.isNotEmpty(p.getValue()))
          .findFirst()
          .flatMap(p -> p.getParameterType().castLong(p.getValue())).orElse(PARAMETER_DELAY_DEFAULT);

      ShutDown shutdown = new ShutDown(getApplicationContext(), delay);

      shutdown.shutdown();

      messages.add("restart scheduled in %s seconds".formatted(delay));

      return resultBuilder.finishedDate(new Date()).status(StatusType.SUCCESS).messages(messages).build();
    } catch (Exception e) {
      messages.add("error, see logs: %s".formatted(e.getMessage()));
      return resultBuilder.messages(messages).finishedDate(new Date()).status(StatusType.FAILURE).build();
    }
  }

  @Override
  public ActionMetadata getMetadata() {
    return ActionMetadata.builder()
        .key(ACTION_KEY)
        .title("Restart Action")
        .description("An action to restart the docker container.")
        .allowedParameters(List.of(
            ActionParameter.builder().key(PARAMETER_DELAY)
                .description("delay before restarting. Default to %s seconds".formatted(PARAMETER_DELAY_DEFAULT))
                .required(true)
                .parameterType(ActionParameterType.LONG)
                .value(PARAMETER_DELAY_DEFAULT + "")
                .build()))
        .defaultCronValue("0 30 4 * * ?")
        .build();
  }


  @Override
  public boolean noOp() {
    return true;
  }

  @Override
  public String getKey() {
    return ACTION_KEY;
  }

  record ShutDown(Optional<ConfigurableApplicationContext> ctx, long delay) {

    public ShutDown(ApplicationContext context, long delay) {
      this(Optional.ofNullable(context).filter(ctx -> ctx instanceof ConfigurableApplicationContext)
          .map(ctx -> (ConfigurableApplicationContext) ctx), delay);
    }

    void shutdown() {
      try {
        log.info("Shutting down, bye...");
      } finally {
        Thread thread = new Thread(this::performShutdown);
        thread.setContextClassLoader(getClass().getClassLoader());
        thread.start();
      }
    }

    private void performShutdown() {
      try {
        Thread.sleep(delay);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      }
      this.ctx.orElseThrow(() -> new RuntimeException("no context to restart")).close();
    }

  }
}
