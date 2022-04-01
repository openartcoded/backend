package tech.artcoded.websitev2.pages.dossier;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface DossierRepository extends MongoRepository<Dossier, String> {
  List<Dossier> findByOrderByUpdatedDateDesc();

  Optional<Dossier> findOneByFeeIdsIsContaining(String id);

  Optional<Dossier> findOneByClosedIsFalse();

  Optional<Dossier> findOneByClosedIsTrueAndIdIs(String id);

  List<Dossier> findByClosedOrderByUpdatedDateDesc(boolean closed);
}
