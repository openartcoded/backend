package tech.artcoded.websitev2.mongodb;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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

  @PostMapping("/restore")
  public ResponseEntity<Void> restore(@RequestParam("archiveName") String archiveName,
                                      @RequestParam("from") String from,
                                      @RequestParam("to") String to) {
    log.info("check cache...");
    log.info("call to mongo restore...");
    this.managementService.restore(archiveName, from, to);
    return ResponseEntity.accepted().build();
  }

  @PostMapping("/dump")
  public ResponseEntity<Void> dump() {
    log.info("call to mongo dump...");
    this.managementService.dump();
    return ResponseEntity.accepted().build();
  }

  @PostMapping("/download")
  public ResponseEntity<ByteArrayResource> download(@RequestParam("archiveName") String archiveName) {
    return RestUtil.transformToByteArrayResource(IdGenerators.get()
                                                             .concat(".zip"), "application/zip", managementService.download(archiveName));
  }
}
