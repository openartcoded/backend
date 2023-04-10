package tech.artcoded.websitev2.pages.invoice;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface InvoiceGenerationRepository extends MongoRepository<InvoiceGeneration, String> {
  List<InvoiceGeneration> findByLogicalDeleteIsFalseOrderByDateCreationDesc();

  Optional<InvoiceGeneration> findFirstByLogicalDeleteIsFalseOrderByDateCreationDesc();

  Optional<InvoiceGeneration> findByTimesheetId(String tsId);

  List<InvoiceGeneration> findByLogicalDeleteIsFalse();

  Page<InvoiceGeneration> findByLogicalDeleteIsAndArchivedIs(boolean logicalDelete,
      boolean archived,
      Pageable pageable);

  List<InvoiceGeneration> findByLogicalDeleteIsAndArchivedIsOrderByDateOfInvoiceDesc(boolean logicalDelete,
      boolean archived);

  long countByFreemarkerTemplateId(String id);

  boolean existsByInvoiceNumber(String invoiceNumber);
}
