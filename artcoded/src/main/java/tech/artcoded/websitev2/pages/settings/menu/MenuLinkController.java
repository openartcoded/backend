package tech.artcoded.websitev2.pages.settings.menu;

import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.Principal;
import java.util.Date;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings/menu-link")
@Slf4j
public class MenuLinkController {
  private final MenuLinkRepository repository;
  private final ObjectMapper mapper;

  public MenuLinkController(MenuLinkRepository repository,
      ObjectMapper mapper) {
    this.repository = repository;
    this.mapper = mapper;
  }

  @PostMapping("/save")
  public ResponseEntity<MenuLink> createOrUpdate(@RequestBody MenuLink menuLink) {
    MenuLink link = ofNullable(menuLink.getId())
        .flatMap(repository::findById)
        .map(MenuLink::toBuilder)
        .orElseGet(menuLink::toBuilder)
        .routerLink(menuLink.getRouterLink())
        .updatedDate(new Date())
        .routerLinkActiveOptions(menuLink.getRouterLinkActiveOptions())
        .icon(menuLink.getIcon())
        .order(menuLink.getOrder())
        .show(menuLink.isShow())
        .description(menuLink.getDescription())
        .title(menuLink.getTitle())
        .numberOfTimesClicked(0L)
        .build();
    return ResponseEntity.ok(repository.save(link));
  }

  @PostMapping("/clicked")
  public ResponseEntity<Void> clicked(@RequestParam("id") String id) {
    Thread.startVirtualThread(() -> this.repository.incrementCount(id));
    return ResponseEntity.accepted().build();
  }

  @GetMapping("/top-3")
  public List<MenuLink> top3() {
    return repository.findTop3ByOrderByNumberOfTimesClickedDesc()
        .stream()
        .filter(ml -> ml.numberOfTimesClicked > 0L)
        .toList();
  }

  @GetMapping
  public List<MenuLink> findAll(Principal principal) {
    UserDetails user = (UserDetails) principal;
    var links = repository.findByOrderByOrderAsc();
    return links.stream()
        .filter(link -> user.getAuthorities()
            .stream()
            .map(a -> a.getAuthority())
            .anyMatch(a -> link.getRoles().contains(a)))
        .toList();
  }

  @PostMapping("/import")
  public void importAll(@RequestBody List<MenuLink> links)
      throws JsonProcessingException {
    List<MenuLink> backup = repository.findAll();
    log.info("backup menu in the logs...");
    log.info(mapper.writeValueAsString(backup));
    repository.deleteAll();
    repository.saveAll(links);
  }

  @DeleteMapping
  public ResponseEntity<Void> delete(@RequestParam("id") String id) {
    repository.deleteById(id);
    return ResponseEntity.accepted().build();
  }
}
