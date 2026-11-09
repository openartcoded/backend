package tech.artcoded.websitev2.pages.report;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.artcoded.websitev2.action.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

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
    public Optional<ActionRequest> getDefaultActionRequest() {
        return Optional.of(ActionRequest.builder().actionKey(ACTION_KEY).parameters(List.of()).persistResult(true)
                .sendMail(false).build());
    }

    @Override
    public boolean shouldNotRun(List<ActionParameter> parameters) {
        return unusedTags().isEmpty();
    }

    @Override
    public ActionResult run(List<ActionParameter> parameters) {
        var resultBuilder = this.actionResultBuilder(parameters);
        List<String> messages = new ArrayList<>();
        try {
            messages.add("cleanup tags");
            // peek() was used for the logging side effect: it is not guaranteed to run, do it explicitly
            for (var postTag : unusedTags()) {
                messages.add("deleting unused tag %s".formatted(postTag.getTag()));
                postTagRepository.delete(postTag);
            }
            return resultBuilder.finishedDate(new Date()).messages(messages).build();
        } catch (Exception e) {
            log.error("error while executing action", e);
            messages.add("error, see logs: %s".formatted(e.getMessage()));
            return resultBuilder.finishedDate(new Date()).messages(messages).status(StatusType.FAILURE).build();
        }
    }

    private List<PostTag> unusedTags() {
        return this.postTagRepository.findAll().stream().filter(postTag -> !postRepository.existsByTags(postTag.getTag()))
                .toList();
    }

    @Override
    public ActionMetadata getMetadata() {
        return ActionMetadata.builder().key(ACTION_KEY).title("Blog Tag Cleanup Action")
                .description("An action to cleanup unused tags").allowedParameters(List.of())
                .defaultCronValue("0 5 0 * * *").build();
    }

    @Override
    public String getKey() {
        return ACTION_KEY;
    }
}
