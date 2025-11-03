package tech.artcoded.websitev2.pages.memzagram;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.CachePut;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tech.artcoded.websitev2.notification.NotificationService;
import tech.artcoded.websitev2.rest.util.MockMultipartFile;
import tech.artcoded.websitev2.upload.IFileUploadService;
import tech.artcoded.websitev2.upload.ILinkable;
import tech.artcoded.websitev2.utils.helper.IdGenerators;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.lang.Boolean.TRUE;
import static java.util.Optional.ofNullable;
import static net.coobird.thumbnailator.Thumbnailator.createThumbnail;
import static tech.artcoded.websitev2.utils.func.CheckedSupplier.toSupplier;

@Service
@Slf4j
public class MemZaGramService implements ILinkable {

    private final MemZaGramRepository repository;
    private final IFileUploadService fileUploadService;
    private final NotificationService notificationService;
    private static final String MEMZ_ADDED = "MEMZ_ADDED";
    private static final String MEMZ_DELETED = "MEMZ_DELETED";

    public MemZaGramService(MemZaGramRepository repository, IFileUploadService fileUploadService,
            NotificationService notificationService) {
        this.repository = repository;
        this.fileUploadService = fileUploadService;
        this.notificationService = notificationService;
    }

    @Async
    public void save(String id, String title, String description, Boolean visible, Date dateOfVisibility,
            MultipartFile imageUpload) {
        MemZaGram memz = ofNullable(id).filter(StringUtils::isNotEmpty).flatMap(this.repository::findById)
                .orElseGet(() -> MemZaGram.builder().id(IdGenerators.get()).build());

        log.info("persisting new image or update visibility...");

        String imageUploadId = this.uploadAndDeleteIfExist(imageUpload, memz.getId(), memz.getImageUploadId(), visible);

        log.info("generating thumbnail...");

        String thumbnailUploadId = ofNullable(imageUpload).map(upl -> {
            byte[] thumbnailBytes = toSupplier(() -> {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                createThumbnail(new ByteArrayInputStream(upl.getBytes()), bos, 300, 300);
                return bos.toByteArray();
            }).get();
            String fileName = "thumb_".concat(ofNullable(upl.getOriginalFilename()).orElse(""));
            return MockMultipartFile.builder().bytes(thumbnailBytes).name(fileName)
                    .contentType(imageUpload.getContentType()).originalFilename(fileName).build();
        }).map(thumbMultipartFile -> this.uploadAndDeleteIfExist(thumbMultipartFile, memz.getId(),
                memz.getThumbnailUploadId(), visible))
                .orElseGet(() -> fileUploadService
                        .updateVisibility(memz.getThumbnailUploadId(), id, TRUE.equals(visible)).orElse(null));

        log.info("saving memz to mongo...");

        MemZaGram save = repository.save(memz.toBuilder().title(title).description(description).updatedDate(new Date())
                .visible(visible).dateOfVisibility(dateOfVisibility).imageUploadId(imageUploadId)
                .thumbnailUploadId(thumbnailUploadId).build());
        notificationService.sendEvent("memz %s added or updated".formatted(save.getTitle()), MEMZ_ADDED, save.getId());
    }

    public Page<MemZaGram> findByVisibleIsTrueOrderByCreatedDateDesc(Pageable pageable) {
        return repository.findByVisibleIsTrueOrderByCreatedDateDesc(pageable);
    }

    public Page<MemZaGram> findAll(Pageable pageable) {
        return repository.findAll(pageable);
    }

    private String uploadAndDeleteIfExist(MultipartFile file, String id, String uploadId, Boolean visibility) {
        String newUploadId;
        Optional<String> optionalImageUploadId = ofNullable(file).filter(Predicate.not(MultipartFile::isEmpty))
                .map(upload -> this.fileUploadService.upload(upload, id, TRUE.equals(visibility)));

        if (optionalImageUploadId.isEmpty()) {
            newUploadId = ofNullable(uploadId)
                    .flatMap(uplId -> fileUploadService.updateVisibility(uplId, id, TRUE.equals(visibility)))
                    .orElseThrow(() -> new RuntimeException("You cannot save without an image!"));
        } else {
            newUploadId = optionalImageUploadId.get();
            ofNullable(uploadId).ifPresent(fileUploadService::delete);
        }
        return newUploadId;
    }

    @Async
    public void delete(String id) {
        repository.findById(id).ifPresent(memz -> {
            fileUploadService.deleteByCorrelationId(id);
            repository.delete(memz);
            notificationService.sendEvent("memz %s deleted".formatted(memz.getTitle()), MEMZ_DELETED, memz.getId());
        });
    }

    public void incrementViewsCount(String id) {
        repository.findById(id)
                .map(memZaGram -> memZaGram.toBuilder().viewsCount(memZaGram.getViewsCount() + 1).build())
                .ifPresent(repository::save);
    }

    @Override
    @CachePut(cacheNames = "memz_correlation_links", key = "#correlationId")
    public String getCorrelationLabel(String correlationId) {
        return this.repository.findById(correlationId).map(toLabel()).orElse(null);
    }

    private Function<? super MemZaGram, ? extends String> toLabel() {
        return mem -> "Memz '%s' ".formatted(mem.getTitle());
    }

    @Override
    @CachePut(cacheNames = "memz_all_correlation_links", key = "'allLinks'")
    public Map<String, String> getCorrelationLabels(Collection<String> correlationIds) {
        return this.repository.findAllById(correlationIds).stream()
                .map(f -> Map.entry(f.getId(), this.toLabel().apply(f)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public void updateOldId(String correlationId, String oldId, String newId) {
        this.repository.findById(correlationId).ifPresent(p -> {
            var changed = false;
            if (oldId.equals(p.getImageUploadId())) {
                p.setImageUploadId(newId);
                changed = true;
            }
            if (oldId.equals(p.getThumbnailUploadId())) {
                p.setThumbnailUploadId(newId);
                changed = true;
            }
            if (changed) {
                this.repository.save(p.toBuilder().updatedDate(new Date()).build());
            }
        });

    }
}
