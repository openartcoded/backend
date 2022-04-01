package tech.artcoded.websitev2.pages.blog;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.artcoded.websitev2.action.Action;
import tech.artcoded.websitev2.action.ActionMetadata;
import tech.artcoded.websitev2.action.ActionParameter;
import tech.artcoded.websitev2.action.ActionResult;
import tech.artcoded.websitev2.action.StatusType;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;


@Component
@Slf4j
public class TagCleanupAction implements Action {
  public static final String ACTION_KEY = "BLOG_TAG_CLEANUP_ACTION";

  private final PostTagRepository postTagRepository;
  private final PostRepository postRepository;

  public TagCleanupAction(PostTagRepository postTagRepository, PostRepository postRepository) {
    this.postTagRepository = postTagRepository;
    this.postRepository = postRepository;
  }

  @Override
  public ActionResult run(List<ActionParameter> parameters) {
    var resultBuilder = this.actionResultBuilder(parameters);
    List<String> messages = new ArrayList<>();
    try {
      messages.add("cleanup tags");
      this.postTagRepository.findAll().stream()
                            .filter(postTag -> !postRepository.existsByTags(postTag.getTag()))
                            .peek(postTag -> messages.add("deleting unused tag %s".formatted(postTag.getTag())))
                            .forEach(postTagRepository::delete);
      return resultBuilder.finishedDate(new Date()).messages(messages).build();
    }
    catch (Exception e) {
      log.error("error while executing action", e);
      messages.add("error, see logs: %s".formatted(e.getMessage()));
      return resultBuilder.finishedDate(new Date()).messages(messages).status(StatusType.FAILURE).build();
    }
  }

  @Override
  public ActionMetadata getMetadata() {
    return ActionMetadata.builder()
                         .key(ACTION_KEY)
                         .title("Blog Tag Cleanup Action")
                         .description("An action to cleanup unused tags")
                         .allowedParameters(List.of())
                         .defaultCronValue("0 */5 * * * *")
                         .build();
  }

  @Override
  public String getKey() {
    return ACTION_KEY;
  }
}
