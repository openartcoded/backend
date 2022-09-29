package tech.artcoded.websitev2.upload;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface FileUploadRepository extends MongoRepository<FileUpload, String> {
  Optional<FileUpload> findByIdAndPublicResourceTrue(String id);

  List<FileUpload> findByCorrelationIdAndPublicResourceIs(String id, boolean publicResource);

  List<FileUpload> findByCorrelationId(String id);
}
