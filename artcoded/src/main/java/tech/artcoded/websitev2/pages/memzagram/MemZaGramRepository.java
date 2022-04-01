package tech.artcoded.websitev2.pages.memzagram;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Date;
import java.util.List;

public interface MemZaGramRepository extends MongoRepository<MemZaGram, String> {
  Page<MemZaGram> findByVisibleIsTrueOrderByCreatedDateDesc(Pageable pageable);

  List<MemZaGram> findByVisibleIsFalseAndDateOfVisibilityIsBefore(Date dateOfVisibility);

  List<MemZaGram> findByThumbnailUploadIdIsNull();
}
