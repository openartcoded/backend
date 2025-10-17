package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;
import tech.artcoded.websitev2.pages.memzagram.MemZaGramRepository;
import tech.artcoded.websitev2.rest.util.MockMultipartFile;
import tech.artcoded.websitev2.upload.FileUploadService;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static java.util.Optional.ofNullable;
import static net.coobird.thumbnailator.Thumbnailator.createThumbnail;
import static tech.artcoded.websitev2.utils.func.CheckedSupplier.toSupplier;

@Slf4j
@ChangeUnit(id = "generate-thumbnail-if-not-present", order = "3", author = "Nordine Bittich")
public class CHANGE_LOG_03_GenerateThumbnailIfNotPresentChangeLog {

    @Execution
    public void run(MemZaGramRepository repository, FileUploadService fileUploadService) throws Exception {
        repository.findByThumbnailUploadIdIsNull()
                .forEach(memz -> ofNullable(memz.getImageUploadId()).flatMap(fileUploadService::findOneById)
                        .map(fileUploadService::toMockMultipartFile).ifPresent(image -> {
                            log.info("perform migration for memz {}", memz.getId());
                            byte[] thumbnailBytes = toSupplier(() -> {
                                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                                createThumbnail(new ByteArrayInputStream(image.getBytes()), bos, 300, 300);
                                return bos.toByteArray();
                            }).get();
                            String fileName = "thumb_".concat(ofNullable(image.getOriginalFilename()).orElse(""));
                            MultipartFile thumb = MockMultipartFile.builder().bytes(thumbnailBytes).name(fileName)
                                    .contentType(image.getContentType()).originalFilename(fileName).build();
                            String thumbUploadId = fileUploadService.upload(thumb, memz.getId(), memz.isVisible());
                            repository.save(memz.toBuilder().thumbnailUploadId(thumbUploadId).build());
                            log.info("migration for memz {} done", memz.getId());
                        }));
    }

    @RollbackExecution
    public void rollbackExecution(MemZaGramRepository repository) {
    }
}
