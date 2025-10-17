package tech.artcoded.websitev2.pages.fee;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface LabelRepository extends MongoRepository<Label, String> {
    Optional<Label> findByNameIgnoreCase(String name);

    Optional<Label> findByColorHex(String color);

}
