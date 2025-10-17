package tech.artcoded.websitev2.action;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Date;

public interface ActionResultRepository extends MongoRepository<ActionResult, String> {
    Page<ActionResult> findByActionKeyOrderByCreatedDateDesc(String actionKey, Pageable pageable);

    void deleteByFinishedDateBefore(Date date);
}
