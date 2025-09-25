package tech.artcoded.websitev2.pages.client;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tech.artcoded.event.v1.client.BillableClientCreatedOrUpdated;
import tech.artcoded.event.v1.client.BillableClientDeleted;
import tech.artcoded.event.v1.client.BillableClientDocumentAddedOrUpdated;
import tech.artcoded.websitev2.event.ExposedEventService;
import tech.artcoded.websitev2.notification.NotificationService;
import tech.artcoded.websitev2.upload.FileUploadService;

import java.util.Date;
import java.util.List;
import java.util.Optional;
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
  private final ExposedEventService exposedEventService;

  public BillableClientService(BillableClientRepository repository, FileUploadService fileUploadService,
      NotificationService notificationService, ExposedEventService exposedEventService) {
    this.repository = repository;
    this.fileUploadService = fileUploadService;
    this.notificationService = notificationService;
    this.exposedEventService = exposedEventService;
  }

  public List<BillableClient> findByContractStatus(ContractStatus status) {
    return repository.findByContractStatus(status);
  }

  public List<BillableClient> findAll() {
    return repository.findByOrderByContractStatusDesc();
  }

  // less than 10 clients, most are inactive. do not need optimization for now
  public Optional<BillableClient> findOneByCompanyNumber(String vatNumber) {
    return findAll().stream().filter(client -> client.getCompanyNumber().equalsIgnoreCase(vatNumber)).findFirst();
  }

  public Optional<BillableClient> findById(String id) {
    return repository.findById(id);
  }

  public BillableClient save(BillableClient client) {
    BillableClient clientSaved = repository.save(ofNullable(client.getId())
        .flatMap(repository::findById)
        .orElseGet(BillableClient.builder()::build)
        .toBuilder()
        .rate(client.getRate())
        .city(client.getCity())
        .name(client.getName())
        .defaultWorkingDays(client.getDefaultWorkingDays())
        .imported(client.isImported())
        .taxRate(client.getTaxRate())
        .countryCode(client.getCountryCode())
        .nature(client.getNature())
        .importedDate(client.getImportedDate())
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
    sendEventUpdateClient(clientSaved);
    return client;
  }

  public void delete(String id) {
    repository.findById(id)
        .ifPresent(client -> {
          repository.delete(client);
          exposedEventService.sendEvent(BillableClientDeleted.builder().clientId(id).build());
        });
  }

  @Async
  public void upload(MultipartFile file, String id) {
    record ClientUpload(BillableClient client, String uploadId) {
    }

    repository.findById(id)
        .map(client -> {
          String uploadId = fileUploadService.upload(file, id, false);
          BillableClient clientUpdated = client.toBuilder()
              .documentIds(concat(ofNullable(client.getDocumentIds()).orElseGet(List::of).stream(), Stream.of(uploadId))
                  .toList())
              .build();
          return new ClientUpload(repository.save(clientUpdated), uploadId);
        })
        .ifPresentOrElse(clientUpload -> {
          notificationService.sendEvent("Document added to customer %s".formatted(clientUpload.client.getName()),
              BILLABLE_CLIENT_UPLOAD_ADDED, clientUpload.client.getId());
          exposedEventService.sendEvent(BillableClientDocumentAddedOrUpdated.builder()
              .clientId(clientUpload.client.getId())
              .uploadId(clientUpload.uploadId)
              .build());
        },
            () -> notificationService.sendEvent("Could not upload document. Client with id %s not found".formatted(id),
                BILLABLE_CLIENT_ERROR, id));
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
          notificationService.sendEvent("Document deleted for customer %s".formatted(client.getName()),
              BILLABLE_CLIENT_UPLOAD_DELETED, client.getId());
          exposedEventService.sendEvent(BillableClientDocumentAddedOrUpdated.builder()
              .uploadId(uploadId)
              .clientId(id)
              .build());
        },
            () -> notificationService.sendEvent("Could not delete document. Client with id %s not found".formatted(id),
                BILLABLE_CLIENT_ERROR, id));
  }

  public List<BillableClient> findByContractStatusAndStartDateIsBefore(ContractStatus status, Date date) {
    return repository.findByContractStatusAndStartDateIsBefore(status, date);
  }

  public List<BillableClient> findByContractStatusInAndEndDateIsBefore(List<ContractStatus> statuses, Date date) {
    return repository.findByContractStatusInAndEndDateIsBefore(statuses, date);
  }

  public void updateAll(List<BillableClient> clients) {
    repository.saveAll(clients);
    clients.forEach(this::sendEventUpdateClient);
  }

  private void sendEventUpdateClient(BillableClient client) {
    exposedEventService.sendEvent(BillableClientCreatedOrUpdated.builder()
        .name(client.getName())
        .projectName(client.getProjectName())
        .clientId(client.getId())
        .contractStatus(client.getContractStatus().name())
        .build());

  }
}
