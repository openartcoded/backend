package tech.artcoded.websitev2.mongodb;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.action.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MongoRestoreAction implements Action {
  public static final String ACTION_KEY = "MONGO_RESTORE_ACTION";
  public static final String PARAMETER_ARCHIVE_NAME = "PARAMETER_ARCHIVE_NAME";
  public static final String PARAMETER_FROM = "PARAMETER_FROM";
  public static final String PARAMETER_TO = "PARAMETER_TO";
  private final MongoManagementService mongoManagementService;
  @Value("${spring.data.mongodb.database}")
  private String defaultDatabase;

  public MongoRestoreAction(MongoManagementService mongoManagementService) {
    this.mongoManagementService = mongoManagementService;
  }

  @Override
  public ActionResult run(List<ActionParameter> parameters) {
    var resultBuilder = this.actionResultBuilder(parameters);

    List<String> messages = new ArrayList<>();
    try {
      messages.add("starting action");
      String archiveName = parameters.stream().filter(p -> PARAMETER_ARCHIVE_NAME.equals(p.getKey()))
          .map(ActionParameter::getValue)
          .filter(StringUtils::isNotEmpty)
          .findFirst()
          .orElseThrow(() -> new RuntimeException("archive name missing"));

      String to = parameters.stream().filter(p -> PARAMETER_TO.equals(p.getKey()))
          .map(ActionParameter::getValue)
          .filter(StringUtils::isNotEmpty)
          .findFirst()
          .orElse(defaultDatabase);
      messages.add("archive name: '%s', to: '%s'".formatted(archiveName, to));
      try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        var futureRestoreResult = executor.submit(() -> mongoManagementService.restore(archiveName, to, false));
        messages.addAll(futureRestoreResult.get());
        messages.add("restore done");
        return resultBuilder.messages(messages).finishedDate(new Date()).build();
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
        .title("Mongo Restore Action")
        .description("An action to restore database asynchronously. Will backup the current one before.")
        .allowedParameters(List.of(
            /*
             * ActionParameter.builder().parameterType(ActionParameterType.STRING)
             * .key(PARAMETER_FROM)
             * .parameterType(ActionParameterType.STRING)
             * .description("DEPRECATED - NOT USED ANYMORE")
             * .required(false)
             * .build(),
             * // TODO may be needed one day, so commented for now
             * ActionParameter.builder().parameterType(ActionParameterType.STRING)
             * .key(PARAMETER_TO)
             * .parameterType(ActionParameterType.STRING)
             * .required(false)
             * .description("To which database name. Default to current").build(),
             */
            ActionParameter.builder()
                .parameterType(ActionParameterType.OPTION)
                .key(PARAMETER_ARCHIVE_NAME)
                // todo allow restore from snapshot
                .options(mongoManagementService.dumpList(false).stream().map(d -> Map.entry(d, d))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
                .required(true)
                .description("Archive name").build()))
        .defaultCronValue("0 30 1 1 1 ?")
        .build();
  }

  @Override
  public String getKey() {
    return ACTION_KEY;
  }
}
