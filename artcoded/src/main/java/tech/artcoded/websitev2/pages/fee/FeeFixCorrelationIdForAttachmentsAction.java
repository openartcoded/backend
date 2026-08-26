package tech.artcoded.websitev2.pages.fee;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.action.Action;
import tech.artcoded.websitev2.action.ActionMetadata;
import tech.artcoded.websitev2.action.ActionParameter;
import tech.artcoded.websitev2.action.ActionRequest;
import tech.artcoded.websitev2.action.ActionResult;
import tech.artcoded.websitev2.action.StatusType;
import tech.artcoded.websitev2.upload.IFileUploadService;

@Slf4j
@Component
public class FeeFixCorrelationIdForAttachmentsAction implements Action {

    public static final String ACTION_KEY = "EXPENSE_FIX_CORRELATION_ID_FOR_ATTACHMENTS";
    private final FeeRepository repository;
    private final IFileUploadService fileService;

    public FeeFixCorrelationIdForAttachmentsAction(FeeRepository repository, IFileUploadService fileService) {
        this.repository = repository;
        this.fileService = fileService;
    }

    public static ActionMetadata getDefaultMetadata() {
        return ActionMetadata.builder().key(ACTION_KEY).title("Fix correltion id for pdf fee")
                .description("Fix correltion id for file attached to a fee").allowedParameters(List.of())
                .defaultCronValue("0 0 23 * * *").build();

    }

    @Override
    public Optional<ActionRequest> getDefaultActionRequest() {
        return Optional.of(ActionRequest.builder().actionKey(ACTION_KEY).parameters(List.of()).persistResult(true)
                .sendMail(false).build());
    }

    @Override
    public boolean shouldNotRun(List<ActionParameter> parameters) {
        return false;
    }

    @Override
    public ActionResult run(List<ActionParameter> parameters) {
        var resultBuilder = this.actionResultBuilder(parameters);
        List<String> messages = new ArrayList<>();
        try {
            Pageable currentPage = Pageable.ofSize(10);
            Page<Fee> page;

            do {
                page = repository.findAll(currentPage);
                for (var fee : page.getContent()) {
                    var attachmentIds = fee.getAttachmentIds();
                    var attachments = fileService.findAll(attachmentIds).stream()
                            .filter(a -> StringUtils.isAllBlank(a.getCorrelationId())).toList();

                    if (!attachments.isEmpty()) {
                        messages.add("found %s attachments without a correlation id ".formatted(attachments.size()));
                    }
                    attachments.forEach(a -> fileService.updateCorrelationId(fee.getId(), a));
                }
                if (page.hasNext()) {
                    currentPage = currentPage.next();
                } else {
                    currentPage = null;
                }

            } while (currentPage != null);
            return resultBuilder.finishedDate(new Date()).messages(messages).build();
        } catch (Exception e) {
            log.error("error while executing action", e);
            messages.add("error, see logs: %s".formatted(e.getMessage()));
            return resultBuilder.finishedDate(new Date()).messages(messages).status(StatusType.FAILURE).build();

        }
    }

    @Override
    public ActionMetadata getMetadata() {
        return getDefaultMetadata();
    }

    @Override
    public String getKey() {
        return ACTION_KEY;
    }
}
