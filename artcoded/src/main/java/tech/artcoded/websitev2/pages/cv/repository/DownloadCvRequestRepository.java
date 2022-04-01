package tech.artcoded.websitev2.pages.cv.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import tech.artcoded.websitev2.pages.cv.entity.DownloadCvRequest;

public interface DownloadCvRequestRepository extends MongoRepository<DownloadCvRequest, String> {
}
