package tech.artcoded.websitev2.pages.memo;


import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/memo-date")
@Slf4j
public class MemoDateController {
  private final MemoDateRepository memoDateRepository;

  public MemoDateController(MemoDateRepository memoDateRepository) {
    this.memoDateRepository = memoDateRepository;
  }

  @PostMapping
  public List<MemoDate> findAll() {
    return this.memoDateRepository.findByOrderByDateSinceDesc();
  }

  @PostMapping("/save")
  public MemoDate save(@RequestBody MemoDate memoDate) {
    return this.memoDateRepository.save(memoDate);
  }

  @DeleteMapping
  public ResponseEntity<Void> delete(@RequestParam String id) {
    this.memoDateRepository.deleteById(id);
    return ResponseEntity.ok().build();
  }
}
