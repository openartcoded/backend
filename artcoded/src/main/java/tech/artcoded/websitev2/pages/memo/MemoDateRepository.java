package tech.artcoded.websitev2.pages.memo;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MemoDateRepository extends MongoRepository<MemoDate, String> {

    List<MemoDate> findByOrderByDateSinceDesc();
}
