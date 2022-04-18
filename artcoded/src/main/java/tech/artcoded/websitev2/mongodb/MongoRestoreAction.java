package tech.artcoded.websitev2.mongodb;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tech.artcoded.websitev2.action.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class MongoRestoreAction implements Action {
  public static final String ACTION_KEY = "MONGO_RESTORE_ACTION";
  public static final String PARAMETER_ARCHIVE_NAME = "PARAMETER_ARCHIVE_NAME";
  public static final String PARAMETER_FROM = "PARAMETER_FROM";
  public static final String PARAMETER_TO = "PARAMETER_TO";


  @Value("${spring.data.mongodb.database}")
  private String defaultDatabase;

  private final MongoManagementService mongoManagementService;

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
      messages.addAll(mongoManagementService.restore(archiveName, to));
      messages.add("restore done");
      return resultBuilder.messages(messages).finishedDate(new Date()).build();

    } catch (Exception e) {
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
                                /* ActionParameter.builder().parameterType(ActionParameterType.STRING)
                                                .key(PARAMETER_FROM)
                                                .parameterType(ActionParameterType.STRING)
                                                .description("DEPRECATED - NOT USED ANYMORE")
                                                .required(false)
                                                .build(),
                                                // TODO may be needed one day, so commented for now
                                 ActionParameter.builder().parameterType(ActionParameterType.STRING)
                                                .key(PARAMETER_TO)
                                                .parameterType(ActionParameterType.STRING)
                                                .required(false)
                                                .description("To which database name. Default to current").build(),*/
        ActionParameter.builder()
          .parameterType(ActionParameterType.OPTION)
          .key(PARAMETER_ARCHIVE_NAME)
          .options(mongoManagementService.dumpList())
          .required(true)
          .description("Archive name").build()
      ))
      .defaultCronValue("0 30 1 1 1 ?")
      .build();
  }

  @Override
  public String getKey() {
    return ACTION_KEY;
  }
}
