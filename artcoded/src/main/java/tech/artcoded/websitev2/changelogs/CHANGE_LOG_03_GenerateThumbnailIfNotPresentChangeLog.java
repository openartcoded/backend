package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;

import org.openapitools.client.api.UploadRoutesApi;
import tech.artcoded.websitev2.pages.memzagram.MemZaGramRepository;
import tech.artcoded.websitev2.upload.FileUpload;
import tech.artcoded.websitev2.upload.IFileUploadService;

@Slf4j
@ChangeUnit(id = "generate-thumbnail-if-not-present", order = "3", author = "Nordine Bittich")
public class CHANGE_LOG_03_GenerateThumbnailIfNotPresentChangeLog {

    @Execution
    public void run(MemZaGramRepository repository, IFileUploadService fileUploadService, UploadRoutesApi routesApi)
            throws Exception {
        var memzList = repository.findByThumbnailUploadIdIsNull();

        for (var memz : memzList) {
            String imageUploadId = memz.getImageUploadId();

            if (imageUploadId == null) {
                continue;
            }

            var fileUploadOpt = fileUploadService.findOneById(imageUploadId);

            if (!fileUploadOpt.isPresent()) {
                continue;
            }

            FileUpload fileUpload = fileUploadOpt.get();
            String thumbnailId = fileUpload.getThumbnailId();

            if (thumbnailId == null || thumbnailId.isBlank()) {

                var res = routesApi.makeThumbWithHttpInfo(fileUpload.getId());
                var data = res.getData();

                var updatedMemz = memz.toBuilder().thumbnailUploadId(data.getThumbnailId()).build();

                repository.save(updatedMemz);
                log.info("migration for memz {} done", memz.getId());
            }

        }
    }

    @RollbackExecution
    public void rollbackExecution(MemZaGramRepository repository) {
    }
}
