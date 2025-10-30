package tech.artcoded.websitev2.pages.postit;

import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/post-it")
public class PostItController {
    private final PostItRepository repository;

    public PostItController(PostItRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<PostIt> findAll() {
        return repository.findAll();
    }

    @PostMapping
    public PostIt saveOrUpdate(@RequestBody PostIt postit) {
        return repository.save(Optional.ofNullable(postit.getId()).flatMap(repository::findById).map(PostIt::toBuilder)
                .orElseGet(PostIt::builder).note(postit.getNote()).updatedDate(new Date()).build());
    }

    @DeleteMapping
    public void delete(@RequestParam("id") String id) {
        repository.deleteById(id);
    }

}
