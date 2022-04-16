package tech.artcoded.websitev2.pages.fee;


import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tech.artcoded.websitev2.rest.annotation.SwaggerHeaderAuthentication;

import javax.inject.Inject;
import java.util.List;

@RestController
@RequestMapping("/api/label")
@Slf4j
public class LabelController {

  private final LabelService labelService;

  @Inject
  public LabelController(LabelService labelService) {
    this.labelService = labelService;
  }


  @PostMapping("/find-all")
  @SwaggerHeaderAuthentication
  public List<Label> findAll() {
    return labelService.findAll();
  }

  @PostMapping("/find-by-name")
  @SwaggerHeaderAuthentication
  public ResponseEntity<Label> findByName(@RequestParam("name") String name) {
    return labelService
            .findByName(name)
            .map(ResponseEntity::ok)
            .orElseGet(ResponseEntity.notFound()::build);
  }

  @PostMapping("/update-all")
  @SwaggerHeaderAuthentication
  public List<Label> updateAll(@RequestBody List<Label> labels) {
    labelService.saveAll(labels);
    return labelService.findAll();
  }

}