package tech.artcoded.websitev2.pages.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tech.artcoded.websitev2.action.StatusType;

class TagCleanupActionTest {
    private PostTagRepository tags;
    private PostRepository posts;
    private TagCleanupAction action;

    @BeforeEach
    void setUp() {
        tags = mock(PostTagRepository.class);
        posts = mock(PostRepository.class);
        action = new TagCleanupAction(tags, posts);
    }

    @Test
    void unusedTagsAreDeletedAndReported() {
        var used = new PostTag("java");
        var unused = new PostTag("cobol");
        when(tags.findAll()).thenReturn(List.of(used, unused));
        when(posts.existsByTags("java")).thenReturn(true);
        when(posts.existsByTags("cobol")).thenReturn(false);

        assertThat(action.shouldNotRun(List.of())).isFalse();
        var result = action.run(List.of());

        assertThat(result.getStatus()).isEqualTo(StatusType.SUCCESS);
        assertThat(result.getActionKey()).isEqualTo(TagCleanupAction.ACTION_KEY);
        assertThat(result.getMessages()).containsExactly("cleanup tags", "deleting unused tag cobol");
        assertThat(result.getFinishedDate()).isNotNull();
        verify(tags).delete(unused);
        verify(tags, never()).delete(used);
    }

    @Test
    void nothingToDoWhenAllTagsAreUsed() {
        when(tags.findAll()).thenReturn(List.of(new PostTag("java")));
        when(posts.existsByTags("java")).thenReturn(true);

        assertThat(action.shouldNotRun(List.of())).isTrue();
        assertThat(action.run(List.of()).getMessages()).containsExactly("cleanup tags");
        verify(tags, never()).delete(any(PostTag.class));
    }

    @Test
    void failuresAreReportedInTheResult() {
        when(tags.findAll()).thenThrow(new IllegalStateException("db down"));

        var result = action.run(List.of());

        assertThat(result.getStatus()).isEqualTo(StatusType.FAILURE);
        assertThat(result.getMessages()).last().asString().contains("db down");
    }

    @Test
    void metadataDescribesTheAction() {
        assertThat(action.getKey()).isEqualTo(TagCleanupAction.ACTION_KEY);
        assertThat(action.getMetadata().getKey()).isEqualTo(TagCleanupAction.ACTION_KEY);
        assertThat(action.getMetadata().getAllowedParameters()).isEmpty();
        assertThat(action.getDefaultActionRequest()).hasValueSatisfying(r -> {
            assertThat(r.getActionKey()).isEqualTo(TagCleanupAction.ACTION_KEY);
            assertThat(r.isPersistResult()).isTrue();
            assertThat(r.isSendMail()).isFalse();
        });
    }
}
