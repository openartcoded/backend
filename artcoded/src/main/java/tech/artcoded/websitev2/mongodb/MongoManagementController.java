package tech.artcoded.websitev2.mongodb;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mongo-management")
public class MongoManagementController {

  private final MongoManagementService managementService;

  public MongoManagementController(MongoManagementService managementService) {
    this.managementService = managementService;
  }

  @GetMapping
  public List<String> dumpList(
      @RequestParam(required = false, defaultValue = "false", value = "snapshot") boolean snapshot) {
    return managementService.dumpList(snapshot);
  }

  @PostMapping("/download")
  @Deprecated(since = "2024.2.0")
  public ResponseEntity<ByteArrayResource> download(@RequestParam("archiveName") String archiveName,
      @RequestParam(value = "snapshot", defaultValue = "false", required = false) boolean snapshot) {
    return ResponseEntity.badRequest().build();

    // return RestUtil.transformToByteArrayResource(IdGenerators.get()
    // .concat(".zip"), "application/zip", managementService.download(archiveName,
    // snapshot));
  }
}
