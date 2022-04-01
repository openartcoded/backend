package tech.artcoded.websitev2.pages.memzagram;


import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tech.artcoded.websitev2.rest.annotation.SwaggerHeaderAuthentication;
import tech.artcoded.websitev2.rest.util.MockMultipartFile;

import java.util.Date;

import static java.util.Optional.ofNullable;

@RestController
@RequestMapping("/api/memzagram")
@Slf4j
public class MemZaGramController {
  private final MemZaGramService service;

  public MemZaGramController(MemZaGramService service) {
    this.service = service;
  }

  @GetMapping("/public")
  public Page<MemZaGram> findAll(Pageable pageable) {
    return service.findByVisibleIsTrueOrderByCreatedDateDesc(pageable);
  }

  @GetMapping("/all")
  public Page<MemZaGram> adminFindAll(Pageable pageable) {
    return service.findAll(pageable);
  }


  @PostMapping(value = "/submit",
               consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @SwaggerHeaderAuthentication
  public ResponseEntity<Void> save(@RequestParam(value = "id",
                                                 required = false) String id,
                                   @RequestParam("title") String title,
                                   @RequestParam(value = "description",
                                                 required = false) String description,
                                   @RequestParam(value = "visible",
                                                 defaultValue = "false") Boolean visible,
                                   @RequestParam(value = "dateOfVisibility",
                                                 required = false) Date dateOfVisibility,
                                   @RequestPart(value = "imageUpload",
                                                required = false) MultipartFile imageUpload) {

    service.save(id, title, description, visible, dateOfVisibility, ofNullable(imageUpload).map(MockMultipartFile::copy)
                                                                                           .orElse(null));


    return ResponseEntity.accepted().build();
  }

  @PostMapping("/_stat")
  public void incrementViewsCount(@RequestParam("id") String id) {
    service.incrementViewsCount(id);
  }


  @DeleteMapping
  @SwaggerHeaderAuthentication
  public ResponseEntity<Void> delete(@RequestParam("id") String id) {
    service.delete(id);
    return ResponseEntity.accepted().build();
  }
}
