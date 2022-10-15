package tech.artcoded.websitev2.pages.invoice;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface InvoiceGenerationRepository extends MongoRepository<InvoiceGeneration, String> {
  List<InvoiceGeneration> findByLogicalDeleteIsFalseOrderByDateCreationDesc();

  List<InvoiceGeneration> findByLogicalDeleteIsFalse();

  Page<InvoiceGeneration> findByLogicalDeleteIsAndArchivedIs(boolean logicalDelete,
                                                             boolean archived,
                                                             Pageable pageable);

  List<InvoiceGeneration> findByLogicalDeleteIsAndArchivedIsOrderByDateOfInvoiceDesc(boolean logicalDelete, boolean archived);

  long countByFreemarkerTemplateId(String id);
}
