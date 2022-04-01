package tech.artcoded.websitev2.changelogs;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.upload.FileUploadRdfService;
import tech.artcoded.websitev2.upload.FileUploadSearchCriteria;
import tech.artcoded.websitev2.upload.FileUploadService;

import java.io.IOException;

@Slf4j
@ChangeUnit(id = "publish-public-uploads",
            order = "7",
            author = "Nordine Bittich")
public class $7_PublishPublicUploadToTriplestore {

  @RollbackExecution
  public void rollbackExecution(FileUploadRdfService fileUploadRdfService, FileUploadService fileUploadService) {
  }

  @Execution
  public void execute(FileUploadRdfService fileUploadRdfService, FileUploadService fileUploadService) throws IOException {

    var files = fileUploadService.findAll(FileUploadSearchCriteria.builder()
                                                                  .publicResource(Boolean.TRUE)
                                                                  .build()

    );

    log.info("found {} public upload", files.size());

    files.forEach(fileUploadRdfService::publish);

  }
}
