package tech.artcoded.websitev2.mongodb;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.action.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

@Service
@Profile({ "dev", "prod" })
@Slf4j
public class MongoDumpAction implements Action {
  public static final String ACTION_KEY = "MONGO_DUMP_ACTION";
  public static final String ACTION_PARAMETER_SNAPSHOT = "ACTION_PARAMETER_SNAPSHOT";

  private final MongoManagementService mongoManagementService;

  public MongoDumpAction(MongoManagementService mongoManagementService) {
    this.mongoManagementService = mongoManagementService;
  }

  @Override
  public ActionResult run(List<ActionParameter> parameters) {
    var resultBuilder = this.actionResultBuilder(parameters);
    List<String> messages = new ArrayList<>();
    try {
      var snapshot = parameters.stream().filter(p -> ACTION_PARAMETER_SNAPSHOT.equals(p.getKey()))
          .filter(p -> StringUtils.isNotEmpty(p.getValue())).findFirst()
          .map(p -> "yes".equals(p.getValue()) ? true : false).orElse(false);
      messages.add("starting scheduled dump...");
      try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        var futureDumpResult = executor.submit(() -> mongoManagementService.dump(snapshot));
        messages.addAll(futureDumpResult.get());
        messages.add("dump done");
        return resultBuilder.finishedDate(new Date()).status(StatusType.SUCCESS).messages(messages).build();
      }
    } catch (Exception e) {
      log.error("error while executing action", e);

      messages.add("error, see logs: %s".formatted(e.getMessage()));
      return resultBuilder.messages(messages).finishedDate(new Date()).status(StatusType.FAILURE).build();
    }
  }

  @Override
  public ActionMetadata getMetadata() {
    return ActionMetadata.builder()
        .key(ACTION_KEY)
        .title("Mongo Dump Action")
        .description("An action to perform a dump of the database (asynchronously).")
        .allowedParameters(List.of(
            ActionParameter.builder().parameterType(ActionParameterType.OPTION)
                .options(Map.of("yes", "Yes", "no", "No"))
                .key(ACTION_PARAMETER_SNAPSHOT)
                .value("no")
                .description("If it is a snapshot, will not backup the files").build()))
        .defaultCronValue("0 30 1 2,15 * ?")
        .build();
  }

  @Override
  public String getKey() {
    return ACTION_KEY;
  }

}
