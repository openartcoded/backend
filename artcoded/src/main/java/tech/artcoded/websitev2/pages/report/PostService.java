package tech.artcoded.websitev2.pages.report;

import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.cache.annotation.CachePut;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import tech.artcoded.websitev2.upload.IFileUploadService;
import tech.artcoded.websitev2.upload.ILinkable;

@Service
public class PostService implements ILinkable {
    private final PostRepository postRepository;
    private final IFileUploadService fileUploadService;

    public PostService(PostRepository postRepository, IFileUploadService fileUploadService) {
        this.fileUploadService = fileUploadService;
        this.postRepository = postRepository;
    }

    @Override
    @CachePut(cacheNames = "reportpost_correlation_links", key = "#correlationId")
    public String getCorrelationLabel(String correlationId) {
        return this.postRepository.findById(correlationId).map(post -> toLabel(post)).orElse(null);
    }

    private String toLabel(Post post) {
        return "Activity post '%s' ".formatted(post.getTitle());
    }

    public Post addAttachment(String id, MultipartFile[] mfs) {
        var post = this.postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("post %s not found".formatted(id)));
        var uploadIds = fileUploadService.uploadAll(Arrays.asList(mfs), post.getId(), false);
        post.setAttachmentIds(
                Stream.concat(post.getAttachmentIds().stream(), uploadIds.stream()).collect(Collectors.toSet()));
        post.setUpdatedDate(new Date());
        return postRepository.save(post);
    }

    public Page<Post> getBookmarked(Pageable pageable) {
        return postRepository.findByBookmarkedIsOrderByBookmarkedDateDesc(true, pageable);
    }

    public Optional<Post> toggleBookmarked(String id) {
        return postRepository.findById(id).map(fee -> postRepository.save(fee.toBuilder().updatedDate(new Date())
                .bookmarked(!fee.isBookmarked()).bookmarkedDate(fee.isBookmarked() ? null : new Date()).build()));
    }

    public Post toggleProcessAttachment(String postId, String attachmentId) {

        var post = this.postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("post %s not found".formatted(postId)));

        if (!post.getAttachmentIds().contains(attachmentId)) {
            return post;
        }

        var processedIds = new HashSet<>(Optional.ofNullable(post.getProcessedAttachmentIds()).orElse(Set.of()));

        if (processedIds.contains(attachmentId)) {
            processedIds.remove(attachmentId);
        } else {
            processedIds.add(attachmentId);
        }

        var updatedPost = post.toBuilder().processedAttachmentIds(processedIds).updatedDate(new Date()).build();

        return this.postRepository.save(updatedPost);

    }

    public Post removeAttachment(String postId, String attachmentId) {
        var post = this.postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("post %s not found".formatted(postId)));
        if (post.getAttachmentIds().stream().anyMatch(attachmentId::equals)) {
            Thread.startVirtualThread(() -> fileUploadService.delete(attachmentId)); // deleting asynchronously
            return this.postRepository.save(post.toBuilder().updatedDate(new Date())
                    .processedAttachmentIds(Optional.ofNullable(post.getProcessedAttachmentIds()).orElse(Set.of())
                            .stream().filter(Predicate.not(attachmentId::equals)).collect(Collectors.toSet()))

                    .attachmentIds(post.getAttachmentIds().stream().filter(Predicate.not(attachmentId::equals))
                            .collect(Collectors.toSet()))
                    .build());
        }
        return post;
    }

    @Override
    @CachePut(cacheNames = "reportpost_all_correlation_links", key = "'allLinks'")
    public Map<String, String> getCorrelationLabels(Collection<String> correlationIds) {
        return this.postRepository.findAllById(correlationIds).stream()
                .map(doc -> Map.entry(doc.getId(), this.toLabel(doc)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public void updateOldId(String correlationId, String oldId, String newId) {
        this.postRepository.findById(correlationId).ifPresent(post -> {
            var changed = false;
            if (oldId.equals(post.getCoverId())) {
                post.setCoverId(newId);
                changed = true;
            }
            if (Optional.ofNullable(post.getAttachmentIds()).orElse(Set.of()).contains(oldId)) {

                post.setAttachmentIds(
                        Stream.concat(post.getAttachmentIds().stream().filter(p -> !oldId.equals(p)), Stream.of(newId))
                                .collect(Collectors.toSet()));
                changed = true;
            }
            if (changed) {
                this.postRepository.save(post.toBuilder().updatedDate(new Date()).build());
            }
        });
    }

}
