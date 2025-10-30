package tech.artcoded.websitev2.upload;

import java.io.File;
import java.io.InputStream;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface IFileUploadService {

    List<FileUpload> findAll(FileUploadSearchCriteria searchCriteria);

    Page<FileUpload> findAll(FileUploadSearchCriteria searchCriteria, Pageable pageable);

    List<FileUpload> findAll(Collection<String> ids);

    Optional<byte[]> getUploadAsBytes(String id);

    File getFile(FileUpload fileUpload);

    byte[] uploadToByteArray(FileUpload upload);

    InputStream uploadToInputStream(FileUpload upload);

    Optional<FileUpload> findOneById(String id);

    Optional<FileUpload> findOneByIdPublic(String id);

    List<FileUpload> findByCorrelationId(boolean publicResource, String correlationId);

    MultipartFile toMockMultipartFile(FileUpload fileUpload);

    String upload(FileUpload upload, InputStream is, boolean publish);

    String upload(MultipartFile file, String correlationId, boolean isPublic);

    String upload(MultipartFile file, String correlationId, Date date, boolean isPublic);

    void delete(String id);

    void delete(FileUpload upload);

    void deleteAll();

    Optional<String> updateVisibility(String id, String correlationId, boolean publicResource);

    void deleteByCorrelationId(String correlationId);

    File getUploadFolder();

    Optional<FileUpload> toggleBookmarked(String id);

    Set<String> findAllCorrelationIds();
}
