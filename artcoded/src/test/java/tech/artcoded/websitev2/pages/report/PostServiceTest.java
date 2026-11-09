package tech.artcoded.websitev2.pages.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import tech.artcoded.websitev2.upload.IFileUploadService;

class PostServiceTest {
    private PostRepository repository;
    private IFileUploadService uploads;
    private PostService service;

    @BeforeEach
    void setUp() {
        repository = mock(PostRepository.class);
        uploads = mock(IFileUploadService.class);
        service = new PostService(repository, uploads);
        when(repository.save(any(Post.class))).thenAnswer(a -> a.getArgument(0));
    }

    private Post stored(Post post) {
        when(repository.findById(post.getId())).thenReturn(Optional.of(post));
        return post;
    }

    @Test
    void attachmentsAreUploadedAndLinkedToThePost() {
        stored(Post.builder().id("p1").attachmentIds(Set.of("existing")).build());
        when(uploads.uploadAll(anyList(), eq("p1"), anyBoolean())).thenReturn(List.of("new1", "new2"));

        var updated = service.addAttachment("p1", new MultipartFile[] { mock(MultipartFile.class) });

        assertThat(updated.getAttachmentIds()).containsExactlyInAnyOrder("existing", "new1", "new2");
    }

    @Test
    void attachmentsCanBeAddedToALegacyPostWithoutAttachmentField() {
        stored(Post.builder().id("p1").attachmentIds(null).build());
        when(uploads.uploadAll(anyList(), eq("p1"), anyBoolean())).thenReturn(List.of("new1"));

        var updated = service.addAttachment("p1", new MultipartFile[0]);

        assertThat(updated.getAttachmentIds()).containsExactly("new1");
    }

    @Test
    void addingAnAttachmentToAnUnknownPostFails() {
        when(repository.findById("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addAttachment("nope", new MultipartFile[0]))
                .hasMessageContaining("post nope not found");
    }

    @Test
    void processedFlagIsToggled() {
        stored(Post.builder().id("p1").attachmentIds(Set.of("a1")).processedAttachmentIds(null).build());

        var processed = service.toggleProcessAttachment("p1", "a1");
        assertThat(processed.getProcessedAttachmentIds()).containsExactly("a1");

        stored(processed);
        var unprocessed = service.toggleProcessAttachment("p1", "a1");
        assertThat(unprocessed.getProcessedAttachmentIds()).isEmpty();
    }

    @Test
    void unknownAttachmentsCannotBeMarkedAsProcessed() {
        var post = stored(Post.builder().id("p1").attachmentIds(Set.of("a1")).build());

        assertThat(service.toggleProcessAttachment("p1", "other")).isSameAs(post);
        verify(repository, never()).save(any());
    }

    @Test
    void removingAnAttachmentDeletesTheFileAndUnlinksIt() {
        stored(Post.builder().id("p1").attachmentIds(Set.of("a1", "a2")).processedAttachmentIds(Set.of("a1"))
                .build());

        var updated = service.removeAttachment("p1", "a1");

        assertThat(updated.getAttachmentIds()).containsExactly("a2");
        assertThat(updated.getProcessedAttachmentIds()).isEmpty();
        verify(uploads, timeout(5_000)).delete("a1");
    }

    @Test
    void removingAnUnknownAttachmentDoesNothing() {
        var post = stored(Post.builder().id("p1").attachmentIds(null).build());

        assertThat(service.removeAttachment("p1", "a1")).isSameAs(post);
        verify(uploads, after(300).never()).delete(anyString());
    }

    @Test
    void bookmarkIsToggledWithItsDate() {
        stored(Post.builder().id("p1").bookmarked(false).build());

        var bookmarked = service.toggleBookmarked("p1").orElseThrow();
        assertThat(bookmarked.isBookmarked()).isTrue();
        assertThat(bookmarked.getBookmarkedDate()).isNotNull();

        stored(bookmarked);
        var unbookmarked = service.toggleBookmarked("p1").orElseThrow();
        assertThat(unbookmarked.isBookmarked()).isFalse();
        assertThat(unbookmarked.getBookmarkedDate()).isNull();
    }

    @Test
    void replacedUploadIdsAreUpdatedInCoverAndAttachments() {
        stored(Post.builder().id("p1").coverId("old").attachmentIds(Set.of("old", "keep")).build());

        service.updateOldId("p1", "old", "new");

        verify(repository).save(argThat((Post p) -> "new".equals(p.getCoverId())
                && p.getAttachmentIds().equals(Set.of("new", "keep"))));
    }

    @Test
    void unrelatedUploadIdChangesDoNotSave() {
        stored(Post.builder().id("p1").coverId("cover").attachmentIds(null).build());

        service.updateOldId("p1", "old", "new");

        verify(repository, never()).save(any());
    }

    @Test
    void correlationLabels() {
        stored(Post.builder().id("p1").title("Weekly").build());
        when(repository.findAllById(List.of("p1", "p2")))
                .thenReturn(List.of(Post.builder().id("p1").title("Weekly").build(),
                        Post.builder().id("p2").title("Monthly").build()));

        assertThat(service.getCorrelationLabel("p1")).isEqualTo("Activity post 'Weekly' ");
        assertThat(service.getCorrelationLabel("unknown")).isNull();
        assertThat(service.getCorrelationLabels(List.of("p1", "p2"))).containsEntry("p1", "Activity post 'Weekly' ")
                .containsEntry("p2", "Activity post 'Monthly' ");
    }
}
