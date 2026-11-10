package tech.artcoded.websitev2.pages.settings.menu;

import static java.util.Optional.ofNullable;

import tools.jackson.databind.json.JsonMapper;
import java.security.Principal;
import java.util.Date;
import java.util.List;
import tech.artcoded.websitev2.security.oauth.Role;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings/menu-link")
@Slf4j
public class MenuLinkController {
    private final MenuLinkRepository repository;
    private final JsonMapper mapper;

    public MenuLinkController(MenuLinkRepository repository, JsonMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @PostMapping("/save")
    public ResponseEntity<MenuLink> createOrUpdate(@RequestBody MenuLink menuLink) {
        MenuLink link = ofNullable(menuLink.getId()).flatMap(repository::findById).map(MenuLink::toBuilder)
                .orElseGet(menuLink::toBuilder).routerLink(menuLink.getRouterLink()).updatedDate(new Date())
                .routerLinkActiveOptions(menuLink.getRouterLinkActiveOptions()).icon(menuLink.getIcon())
                .order(menuLink.getOrder()).show(menuLink.isShow()).description(menuLink.getDescription())
                .title(menuLink.getTitle()).numberOfTimesClicked(0L).build();
        return ResponseEntity.ok(repository.save(link));
    }

    @PostMapping("/clicked")
    public ResponseEntity<Void> clicked(@RequestParam("id") String id) {
        Thread.startVirtualThread(() -> this.repository.incrementCount(id));
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/top-3")
    public List<MenuLink> top3() {
        return repository.findTop3ByOrderByNumberOfTimesClickedDesc().stream()
                .filter(ml -> ml.numberOfTimesClicked > 0L).toList();
    }

    @GetMapping("/top-5")
    public List<MenuLink> top5() {
        return repository.findTop5ByOrderByNumberOfTimesClickedDesc().stream()
                .filter(ml -> ml.numberOfTimesClicked > 0L).toList();
    }

    @GetMapping
    public List<MenuLink> findAll(Principal principal) {
        var links = repository.findByOrderByOrderAsc();
        var user = tech.artcoded.websitev2.pages.personal.User.fromPrincipal(principal);

        // a link without roles (e.g. created from the settings) would throw a NPE for everyone: admin only
        return links.stream().filter(link -> user.getAuthorities().stream().anyMatch(
                a -> ofNullable(link.getRoles()).orElseGet(() -> List.of(Role.ADMIN.getAuthority())).contains(a)))
                .toList();
    }

    @PostMapping("/import")
    public void importAll(@RequestBody List<MenuLink> links) {
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
