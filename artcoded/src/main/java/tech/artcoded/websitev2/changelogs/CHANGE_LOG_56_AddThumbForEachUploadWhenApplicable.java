
package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.mongodb.MongoManagementService;
import tech.artcoded.websitev2.upload.IFileUploadService;

import java.io.IOException;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.openapitools.client.api.UploadRoutesApi;

@ChangeUnit(id = "add-thumb-for-each-upload-fixed-no-virtualthread", order = "56", author = "Nordine Bittich")
@Slf4j
public class CHANGE_LOG_56_AddThumbForEachUploadWhenApplicable {

    @RollbackExecution
    public void rollbackExecution() {
    }

    @Execution
    public void execute(MongoManagementService mongoManagementService, IFileUploadService fileUploadService,
            UploadRoutesApi routesApi) throws IOException {

        log.warn("FileService V2 migration: generate thumb for each uploads...");
        log.warn("make a dump first");
        log.info(mongoManagementService.dump(false).stream().collect(Collectors.joining("\n")));
        log.warn("dump generated. ping file service...");
        for (var upl : fileUploadService.findAll()) {
            if (Boolean.TRUE.equals(upl.getThumb()) || StringUtils.isNotBlank(upl.getThumbnailId())) {
                log.warn("skip {}", upl);
                continue;
            }
            try {
                log.info("acquiring lock...");
                var res = routesApi.makeThumbWithHttpInfo(upl.getId());
                log.warn("status code: {}, id:{}, thumbnail result: {}", res.getStatusCode(), upl.getId(),
                        res.getData());
            } catch (Exception e) {
                log.warn("could not generate thumb for {}. error:\n{}", upl, e);
            }

        }

    }

}
