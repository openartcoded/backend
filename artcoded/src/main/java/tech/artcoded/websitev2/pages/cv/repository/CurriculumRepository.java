package tech.artcoded.websitev2.pages.cv.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import tech.artcoded.websitev2.pages.cv.entity.Curriculum;

public interface CurriculumRepository extends MongoRepository<Curriculum, String> {
}
