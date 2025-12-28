package tech.artcoded.websitev2.pages.fee;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/label")
public class LabelController {

    private final LabelService labelService;

    public LabelController(LabelService labelService) {
        this.labelService = labelService;
    }

    @PostMapping("/find-all")
    public List<Label> findAll() {
        return labelService.findAll();
    }

    @PostMapping("/find-by-name")
    public ResponseEntity<Label> findByName(@RequestParam("name") String name) {
        return labelService.findByName(name).map(ResponseEntity::ok).orElseGet(ResponseEntity.notFound()::build);
    }

    @PostMapping("/update-all")
    public List<Label> updateAll(@RequestBody List<Label> labels) {
        labelService.saveAll(labels);
        return labelService.findAll();
    }

}
