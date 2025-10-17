package tech.artcoded.websitev2.pages.invoice;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import tech.artcoded.websitev2.peppol.PeppolStatus;

import java.util.List;
import java.util.Optional;

public interface InvoiceGenerationRepository extends MongoRepository<InvoiceGeneration, String> {
    List<InvoiceGeneration> findByLogicalDeleteIsFalseOrderByDateCreationDesc();

    Optional<InvoiceGeneration> findFirstByLogicalDeleteIsFalseOrderByDateCreationDesc();

    Optional<InvoiceGeneration> findByTimesheetId(String tsId);

    List<InvoiceGeneration> findByLogicalDeleteIsFalse();

    long countByLogicalDeleteIsOrArchivedIs(boolean logical, boolean archived);

    Page<InvoiceGeneration> findByLogicalDeleteIsAndArchivedIs(boolean logicalDelete, boolean archived,
            Pageable pageable);

    List<InvoiceGeneration> findByLogicalDeleteIsAndArchivedIsOrderByDateOfInvoiceDesc(boolean logicalDelete,
            boolean archived);

    List<InvoiceGeneration> findByLogicalDeleteIsFalseAndArchivedIsTrueAndPeppolStatusIs(PeppolStatus peppolStatus);

    long countByFreemarkerTemplateId(String id);

    boolean existsByInvoiceNumber(String invoiceNumber);

    boolean existsByCreditNoteInvoiceReference(String invoiceNumber);

    Page<InvoiceGeneration> findByBookmarkedIsOrderByBookmarkedDateDesc(boolean bookmarked, Pageable pageable);

}
