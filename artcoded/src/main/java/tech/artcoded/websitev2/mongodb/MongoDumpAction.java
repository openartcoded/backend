package tech.artcoded.websitev2.mongodb;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import tech.artcoded.websitev2.action.Action;
import tech.artcoded.websitev2.action.ActionMetadata;
import tech.artcoded.websitev2.action.ActionParameter;
import tech.artcoded.websitev2.action.ActionResult;
import tech.artcoded.websitev2.action.StatusType;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
@Slf4j
@Profile({"dev", "prod"})
public class MongoDumpAction implements Action {
  public static final String ACTION_KEY = "MONGO_DUMP_ACTION";

  private final MongoManagementService mongoManagementService;

  public MongoDumpAction(MongoManagementService mongoManagementService) {
    this.mongoManagementService = mongoManagementService;
  }


  @Override
  public ActionResult run(List<ActionParameter> parameters) {
    var resultBuilder = this.actionResultBuilder(parameters);
    List<String> messages = new ArrayList<>();
    messages.add("starting scheduled dump...");
    mongoManagementService.dump();
    return resultBuilder.finishedDate(new Date()).status(StatusType.UNKNOWN).messages(messages).build();
  }

  @Override
  public ActionMetadata getMetadata() {
    return ActionMetadata.builder()
                         .key(ACTION_KEY)
                         .title("Mongo Dump Action")
                         .description("An action to perform a dump of the database (asynchronously).")
                         .allowedParameters(List.of())
                         .defaultCronValue("0 30 1 2,15 * ?")
                         .build();
  }

  @Override
  public String getKey() {
    return ACTION_KEY;
  }

}
