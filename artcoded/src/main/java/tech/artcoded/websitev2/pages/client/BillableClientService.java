package tech.artcoded.websitev2.pages.client;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tech.artcoded.websitev2.notification.NotificationService;
import tech.artcoded.websitev2.upload.FileUploadService;

import java.util.List;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;
import static java.util.stream.Stream.concat;

@Service
public class BillableClientService {
  private static final String BILLABLE_CLIENT_UPLOAD_ADDED = "BILLABLE_CLIENT_UPLOAD_ADDED";
  private static final String BILLABLE_CLIENT_ERROR = "BILLABLE_CLIENT_ERROR";
  private static final String BILLABLE_CLIENT_UPLOAD_DELETED = "BILLABLE_CLIENT_UPLOAD_DELETED";
  private final BillableClientRepository repository;
  private final FileUploadService fileUploadService;
  private final NotificationService notificationService;

  public BillableClientService(BillableClientRepository repository, FileUploadService fileUploadService, NotificationService notificationService) {
    this.repository = repository;
    this.fileUploadService = fileUploadService;
    this.notificationService = notificationService;
  }

  public List<BillableClient> findByContractStatus(ContractStatus status) {
    return repository.findByContractStatus(status);
  }

  public List<BillableClient> findAll() {
    return repository.findAll();
  }

  public BillableClient save(BillableClient client) {
    return repository.save(ofNullable(client.getId())
      .flatMap(repository::findById)
      .orElseGet(BillableClient.builder()::build)
      .toBuilder()
      .rate(client.getRate())
      .city(client.getCity())
      .name(client.getName())
      .maxDaysToPay(client.getMaxDaysToPay())
      .startDate(client.getStartDate())
      .contractStatus(client.getContractStatus())
      .emailAddress(client.getEmailAddress())
      .phoneNumber(client.getPhoneNumber())
      .vatNumber(client.getVatNumber())
      .address(client.getAddress())
      .projectName(client.getProjectName())
      .rateType(client.getRateType())
      .endDate(client.getEndDate()).build());
  }

  public void delete(String id) {
    repository.deleteById(id);
  }

  @Async
  public void upload(MultipartFile file, String id) {
    repository.findById(id)
      .map(client -> client.toBuilder()
        .documentIds(concat(ofNullable(client.getDocumentIds()).orElseGet(List::of).stream(), Stream.of(fileUploadService.upload(file, id, false))).toList())
        .build())
      .map(repository::save)
      .ifPresentOrElse(client -> notificationService.sendEvent("Document added to customer %s".formatted(client.getName()), BILLABLE_CLIENT_UPLOAD_ADDED, client.getId()),
        () -> notificationService.sendEvent("Could not upload document. Client with id %s not found".formatted(id), BILLABLE_CLIENT_ERROR, id));
  }

  @Async
  public void deleteUpload(String id, String uploadId) {
    repository.findById(id)
      .filter(client -> client.getDocumentIds().contains(uploadId))
      .map(client -> client.toBuilder()
        .documentIds(client.getDocumentIds().stream().filter(documentId -> !documentId.equals(uploadId)).toList())
        .build())
      .map(repository::save)
      .ifPresentOrElse(client -> {
          this.fileUploadService.delete(uploadId);
          notificationService.sendEvent("Document deleted for customer %s".formatted(client.getName()), BILLABLE_CLIENT_UPLOAD_DELETED, client.getId());
        },
        () -> notificationService.sendEvent("Could not delete document. Client with id %s not found".formatted(id), BILLABLE_CLIENT_ERROR, id));
  }
}
