package tech.artcoded.websitev2.mongodb;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.artcoded.websitev2.api.helper.IdGenerators;
import tech.artcoded.websitev2.rest.util.RestUtil;

import java.util.List;

@RestController
@RequestMapping("/api/mongo-management")
@Slf4j
public class MongoManagementController {

  private final MongoManagementService managementService;

  public MongoManagementController(MongoManagementService managementService) {
    this.managementService = managementService;
  }

  @GetMapping
  public List<String> dumpList() {
    return managementService.dumpList();
  }


  @PostMapping("/download")
  public ResponseEntity<ByteArrayResource> download(@RequestParam("archiveName") String archiveName) {
    return RestUtil.transformToByteArrayResource(IdGenerators.get()
      .concat(".zip"), "application/zip", managementService.download(archiveName));
  }
}
