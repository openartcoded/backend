package tech.artcoded.websitev2.pages.memzagram;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.artcoded.websitev2.action.Action;
import tech.artcoded.websitev2.action.ActionMetadata;
import tech.artcoded.websitev2.action.ActionParameter;
import tech.artcoded.websitev2.action.ActionResult;
import tech.artcoded.websitev2.action.StatusType;
import tech.artcoded.websitev2.notification.NotificationService;
import tech.artcoded.websitev2.upload.FileUploadService;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
public class MemZaGramAction implements Action {
  public static final String ACTION_KEY = "MEMZ_SET_VISIBLE_ACTION";
  private static final String MEMZ_SET_VISIBLE = "MEMZ_SET_VISIBLE";
  private final NotificationService notificationService;
  private final FileUploadService fileUploadService;
  private final MemZaGramRepository repository;

  public MemZaGramAction(
          NotificationService notificationService, FileUploadService fileUploadService, MemZaGramRepository repository) {
    this.notificationService = notificationService;
    this.fileUploadService = fileUploadService;
    this.repository = repository;
  }

  @Override
  public ActionResult run(List<ActionParameter> parameters) {
    Date date = new Date();
    var resultBuilder = this.actionResultBuilder(parameters).startedDate(date);

    List<String> messages = new ArrayList<>();

    try {
      repository.findByVisibleIsFalseAndDateOfVisibilityIsBefore(date).stream()
                .peek(memZaGram -> messages.add("update visibility for memz wih id " + memZaGram.getId()))
                .filter(memZaGram -> memZaGram.getImageUploadId() != null)
                .map(
                        memz ->
                                memz.toBuilder()
                                    .imageUploadId(fileUploadService.updateVisibility(memz.getImageUploadId(), memz.getId(), true)
                                                                    .orElseThrow(() -> new RuntimeException("could not find image!")))
                                    .thumbnailUploadId(fileUploadService.updateVisibility(memz.getThumbnailUploadId(), memz.getId(), true)
                                                                        .orElseThrow(() -> new RuntimeException("could not find thumbnail!")))
                                    .updatedDate(new Date())
                                    .visible(true)
                                    .build())
                .map(repository::save)
                .forEach(
                        visibleMemz ->
                                notificationService.sendEvent(
                                        "Memz %s set to visible".formatted(visibleMemz.getTitle()), MEMZ_SET_VISIBLE, visibleMemz.getId()));
      return resultBuilder.finishedDate(new Date()).messages(messages).build();

    }
    catch (Exception e) {
      log.error("error while executing action", e);
      messages.add("error, see logs: %s".formatted(e.getMessage()));
      return resultBuilder.messages(messages).finishedDate(new Date()).status(StatusType.FAILURE).build();
    }

  }

  @Override
  public ActionMetadata getMetadata() {
    return ActionMetadata.builder()
                         .key(ACTION_KEY)
                         .title("Memzagram Visibility Update Action")
                         .description("An action to check periodically either if the memz must be set to visible")
                         .allowedParameters(List.of())
                         .defaultCronValue("*/40 * * * * *")
                         .build();
  }

  @Override
  public String getKey() {
    return ACTION_KEY;
  }
}

