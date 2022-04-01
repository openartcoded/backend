package tech.artcoded.websitev2.pages.immo;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tech.artcoded.websitev2.rest.annotation.SwaggerHeaderAuthentication;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/immo-filter")
@Slf4j
public class ImmoFilterController {
  @Value("${application.immo.localIpAddress}")
  private String localIpAddress;

  private final ImmoFilterRepository repository;

  @Inject
  public ImmoFilterController(ImmoFilterRepository repository) {
    this.repository = repository;
  }

  @DeleteMapping
  @SwaggerHeaderAuthentication
  public ResponseEntity<Map.Entry<String, String>> delete(@RequestParam("id") String id) {
    log.warn("immo filter with id {} will be really deleted", id);
    this.repository.deleteById(id);
    return ResponseEntity.ok(Map.entry("message", "immo filter deleted"));
  }

  @PostMapping("/find-all")
  @SwaggerHeaderAuthentication
  public List<ImmoFilter> findAll() {
    return repository.findAll();
  }

  @PostMapping("/filter")
  @SwaggerHeaderAuthentication
  public ResponseEntity<Void> filter(@RequestBody String rawJson, @RequestParam("id") String id) {
    repository.save(ImmoFilter.builder().id(id).rawJson(rawJson).build());
    return ResponseEntity.ok().build();
  }


}
