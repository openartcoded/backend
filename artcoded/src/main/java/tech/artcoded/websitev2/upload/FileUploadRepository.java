package tech.artcoded.websitev2.upload;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface FileUploadRepository extends MongoRepository<FileUpload, String> {
  Optional<FileUpload> findByIdAndPublicResourceTrue(String id);

  List<FileUpload> findByCorrelationIdAndPublicResourceIs(String id, boolean publicResource);

  List<FileUpload> findByCorrelationId(String id);

  @Query(value = "{}", fields = "{ 'correlationId' : 1, '_id': 0 }")
  Set<String> findAllCorrelationIds();
}
