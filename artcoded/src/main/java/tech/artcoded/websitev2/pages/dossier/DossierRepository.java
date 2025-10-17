package tech.artcoded.websitev2.pages.dossier;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface DossierRepository extends MongoRepository<Dossier, String> {
    List<Dossier> findByClosedIsTrueAndBackupDateIsNull();

    Optional<Dossier> findOneByFeeIdsIsContaining(String id);

    Optional<Dossier> findOneByClosedIsFalse();

    Optional<Dossier> findFirstByClosedIsTrueOrderByCreationDateDesc();

    Optional<Dossier> findOneByClosedIsTrueAndIdIs(String id);

    Page<Dossier> findByClosedIs(boolean closed, Pageable page);

    Page<Dossier> findByBookmarkedIsOrderByBookmarkedDateDesc(boolean bookmarked, Pageable page);

    List<Dossier> findByClosedOrderByUpdatedDateDesc(boolean closed);
}
