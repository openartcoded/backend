package tech.artcoded.websitev2.pages.dossier;

import org.springframework.data.domain.Pageable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import tech.artcoded.event.v1.dossier.*;
import tech.artcoded.websitev2.event.ExposedEventService;
import tech.artcoded.websitev2.pages.document.AdministrativeDocumentService;
import tech.artcoded.websitev2.pages.fee.Fee;
import tech.artcoded.websitev2.pages.fee.FeeService;
import tech.artcoded.websitev2.pages.invoice.InvoiceGeneration;
import tech.artcoded.websitev2.pages.invoice.InvoiceService;
import tech.artcoded.websitev2.upload.FileUploadService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;
import static java.util.stream.Stream.concat;

@Service
public class DossierService {
  private final FeeService feeService;
  private final InvoiceService invoiceService;
  private final DossierRepository dossierRepository;
  private final AdministrativeDocumentService documentService;
  private final FileUploadService fileUploadService;

  private final ExposedEventService eventService;
  private final CloseActiveDossierService closeActiveDossierService;

  public DossierService(
      FeeService feeService,
      InvoiceService invoiceService,
      DossierRepository dossierRepository,
      FileUploadService fileUploadService,
      AdministrativeDocumentService documentService,
      ExposedEventService eventService, CloseActiveDossierService closeActiveDossierService) {
    this.feeService = feeService;
    this.invoiceService = invoiceService;
    this.dossierRepository = dossierRepository;
    this.eventService = eventService;
    this.fileUploadService = fileUploadService;
    this.documentService = documentService;
    this.closeActiveDossierService = closeActiveDossierService;
  }

  public Dossier save(Dossier dossier) {
    return this.dossierRepository.save(dossier);
  }

  @CacheEvict(cacheNames = "dossierSummaries", allEntries = true)
  public Dossier closeActiveDossier() {
    Dossier dossier = this.closeActiveDossierService.closeActiveDossier();
    eventService.sendEvent(DossierClosed.builder().uploadId(dossier.getDossierUploadId())
        .name(dossier.getName())
        .dossierId(dossier.getId()).build());
    return dossier;
  }

  public void removeFee(String feeId) {
    var optionalDossier = this.getActiveDossier();
    if (optionalDossier.isPresent()) {
      var dossier = optionalDossier.get();
      var optionalFee = feeService
          .findById(feeId)
          .map(
              fee -> fee.toBuilder()
                  .archived(false)
                  .archivedDate(null)
                  .build())
          .map(feeService::update);
      if (optionalFee.isPresent()) {
        var fee = optionalFee.get();
        this.save(
            dossier.toBuilder()
                .updatedDate(new Date())
                .feeIds(
                    dossier.getFeeIds().stream()
                        .filter(id -> !id.equals(fee.getId()))
                        .collect(Collectors.toSet()))
                .build());

        eventService.sendEvent(ExpenseRemovedFromDossier.builder()
            .dossierId(dossier.getId()).expenseId(fee.getId()).build());

      }
    }

  }

  void processInvoiceForDossier(String invoiceId, Dossier dossier, Date date) {
    invoiceService
        .findById(invoiceId)
        .filter(i -> !i.isArchived())
        .ifPresent(invoiceGeneration -> processInvoice(invoiceGeneration, dossier, date));
  }

  Dossier processInvoice(InvoiceGeneration invoice, Dossier dossier, Date date) {
    invoiceService.update(invoice.toBuilder()
        .archived(true)
        .archivedDate(date)
        .build());
    var d = dossierRepository.save(
        dossier.toBuilder()
            .invoiceIds(
                Stream.concat(
                    Stream.of(invoice.getId()),
                    dossier.getInvoiceIds().stream())
                    .collect(Collectors.toSet()))
            .updatedDate(date)
            .build());

    eventService.sendEvent(InvoiceAddedToDossier.builder()
        .dossierId(d.getId()).invoiceId(invoice.getId()).build());
    return d;

  }

  public void addDocumentToDossier(String documentId) {
    var optionalDossier = this.getActiveDossier();
    if (optionalDossier.isPresent()) {
      var dossier = optionalDossier.get();
      documentService.lockDocument(documentId).ifPresent(doc -> {
        var d = dossierRepository.save(dossier.toBuilder()
            .updatedDate(new Date())
            .documentIds(Stream.concat(
                Stream.of(doc.getId()),
                dossier.getDocumentIds().stream())
                .collect(Collectors.toSet()))
            .build());

        eventService.sendEvent(DocumentAddedToDossier.builder()
            .dossierId(d.getId()).documentId(documentId).build());
      });
    }

  }

  public void removeDocumentFromDossier(String documentId) {
    var optionalDossier = this.getActiveDossier();
    if (optionalDossier.isPresent()) {
      var dossier = optionalDossier.get();
      documentService.unlockDocument(documentId).ifPresent(doc -> {
        var d = dossierRepository.save(dossier.toBuilder()
            .updatedDate(new Date())
            .documentIds(dossier.getDocumentIds().stream().filter(id -> !id.equals(documentId))
                .collect(Collectors.toSet()))
            .build());

        eventService.sendEvent(DocumentRemovedFromDossier.builder()
            .dossierId(d.getId()).documentId(documentId).build());
      });
    }

  }

  public void processInvoiceForDossier(String id) {
    var optionalDossier = this.getActiveDossier();
    optionalDossier.ifPresent(dossier -> processInvoiceForDossier(id, dossier, new Date()));
  }

  public void processFeesForDossier(List<String> feeIds, Dossier dossier, Date date) {
    List<Fee> feesArchived = feeIds.stream()
        .map(feeService::findById)
        .flatMap(Optional::stream)
        .filter(f -> f.getTag() != null && !f.isArchived())
        .toList();
    processFees(feesArchived, dossier, date);
  }

  public Dossier processFees(List<Fee> fees, Dossier dossier, Date date) {
    Set<String> feesArchived = fees.stream()
        .map(
            f -> f.toBuilder()
                .archived(true)
                .updatedDate(date)
                .archivedDate(date)
                .build())
        .map(feeService::update)
        .map(Fee::getId)
        .collect(Collectors.toSet());

    var d = dossierRepository.save(
        dossier.toBuilder()
            .feeIds(
                Stream.concat(dossier.getFeeIds().stream(), feesArchived.stream())
                    .collect(Collectors.toSet()))
            .updatedDate(date)
            .build());

    eventService.sendEvent(ExpensesAddedToDossier.builder()
        .dossierId(d.getId()).expenseIds(feesArchived).build());
    return d;
  }

  public void processFeesForDossier(List<String> feeIds) {
    var optionalDossier = this.getActiveDossier();
    optionalDossier.ifPresent(dossier -> processFeesForDossier(feeIds, dossier, new Date()));
  }

  public Dossier newDossier(Dossier dossier) {
    if (this.getActiveDossier().isEmpty()) {
      Dossier build = Dossier.builder().name(dossier.getName())
          .tvaDue(dossier.getTvaDue())
          .advancePayments(dossier.getAdvancePayments())
          .description(dossier.getDescription()).build();
      Dossier savedDossier = dossierRepository.save(build);
      eventService.sendEvent(DossierCreated.builder()
          .dossierId(savedDossier.getId())
          .description(savedDossier.getDescription())
          .tvaDue(savedDossier.getTvaDue())
          .name(savedDossier.getName()).build());
      return savedDossier;
    } else {
      throw new RuntimeException("You cannot open two dossiers at the same time");
    }
  }

  public void delete() {
    this.getActiveDossier()
        .filter(d -> !d.isClosed() && d.getInvoiceIds().isEmpty() && d.getFeeIds().isEmpty())
        .ifPresent(dossier -> {
          this.dossierRepository.delete(dossier);
          eventService.sendEvent(DossierDeleted.builder().dossierId(dossier.getId()).build());
        });
  }

  public Dossier updateActiveDossier(Dossier dossier) {
    Dossier toSave = getActiveDossier()
        .map(
            d -> d.toBuilder()
                .name(dossier.getName())
                .description(dossier.getDescription())
                .tvaDue(dossier.getTvaDue())
                .advancePayments(ofNullable(dossier.getAdvancePayments()).orElseGet(List::of))
                .updatedDate(new Date())
                .build())
        .orElseThrow(() -> new RuntimeException("No active dossier found"));
    Dossier updated = this.save(toSave);
    eventService.sendEvent(DossierUpdated.builder().dossierId(updated.getId())
        .tvaDue(dossier.getTvaDue())
        .description(dossier.getDescription())
        .name(updated.getName()).build());
    return updated;
  }

  /**
   * Only tva can be changed !!!
   *
   * @param dossier dossier
   * @return the dossier
   */
  public Dossier recallForModification(Dossier dossier) {
    var toSave = dossierRepository
        .findOneByClosedIsTrueAndIdIs(dossier.getId())
        .map(
            d -> d.toBuilder()
                .tvaDue(dossier.getTvaDue())
                .advancePayments(ofNullable(dossier.getAdvancePayments()).orElseGet(List::of))
                .updatedDate(new Date())
                .recalledForModification(true)
                .recalledForModificationDate(new Date())
                .build())
        .orElseThrow(() -> new RuntimeException("Dossier not found"));
    Dossier save = this.save(toSave);
    this.eventService.sendEvent(DossierRecallForModification.builder()
        .dossierId(save.getId())
        .tvaDue(save.getTvaDue())
        .build());
    return save;
  }

  public void removeInvoice(String invoiceId) {
    var optionalDossier = this.getActiveDossier();
    if (optionalDossier.isPresent()) {
      var dossier = optionalDossier.get();
      var optionalInvoice = invoiceService
          .findById(invoiceId)
          .map(
              i -> i.toBuilder()
                  .archived(false)
                  .archivedDate(null)
                  .build())
          .map(invoiceService::update);
      if (optionalInvoice.isPresent()) {
        var invoice = optionalInvoice.get();
        this.save(
            dossier.toBuilder()
                .updatedDate(new Date())
                .invoiceIds(
                    dossier.getInvoiceIds().stream()
                        .filter(id -> !id.equals(invoice.getId()))
                        .collect(Collectors.toSet()))
                .build());

        eventService.sendEvent(InvoiceRemovedFromDossier.builder()
            .dossierId(dossier.getId()).invoiceId(invoice.getId()).build());
      }
    }
  }

  public Dossier fromPreviousDossier() {
    var copy = Dossier.builder().id(null).creationDate(null).updatedDate(null);
    return dossierRepository.findFirstByClosedIsTrueOrderByCreationDateDesc()
        .map(d -> copy.name(d.getName() + "(copy)").description(d.getDescription())
            .advancePayments(d.getAdvancePayments()).build())
        .orElseGet(copy::build);
  }

  @CachePut(cacheNames = "dossierSummaries", key = "'closedDossierSummaries'", condition = "#closed == true")
  public List<DossierSummary> getAllSummaries(boolean closed) {
    var dossiers = findAll(closed);
    var allInvoiceIds = dossiers.stream().flatMap(d -> d.getInvoiceIds().stream()).toList();
    var allExpenseIds = dossiers.stream().flatMap(d -> d.getFeeIds().stream()).toList();
    var allInvoices = invoiceService.findAll(allInvoiceIds);
    var allExpenses = feeService.findAll(allExpenseIds);
    return findAll(closed).stream()
        .parallel()
        .map(dossier -> this.convertToSummary(dossier, allInvoices, allExpenses))
        .toList();
  }

  public List<DossierSummary> getSummaries(List<String> ids) {
    var dossiers = dossierRepository.findAllById(ids);
    var allInvoiceIds = dossiers.stream().flatMap(d -> d.getInvoiceIds().stream()).toList();
    var allExpenseIds = dossiers.stream().flatMap(d -> d.getFeeIds().stream()).toList();
    var allInvoices = invoiceService.findAll(allInvoiceIds);
    var allExpenses = feeService.findAll(allExpenseIds);

    return dossierRepository.findAllById(ids).stream()
        .parallel()
        .map(dossier -> this.convertToSummary(dossier, allInvoices, allExpenses))
        .toList();
  }

  public DossierSummary getSummary(String id) {
    return findById(id)
        .map(dossier -> {
          var allExpenses = feeService.findAll(new ArrayList<>(dossier.getFeeIds()));
          var allInvoices = invoiceService.findAll(new ArrayList<>(dossier.getInvoiceIds()));
          return this.convertToSummary(dossier, allInvoices, allExpenses);
        })
        .orElseThrow(() -> new RuntimeException("dossier not found"));
  }

  private DossierSummary convertToSummary(Dossier dossier, List<InvoiceGeneration> allInvoices, List<Fee> allExpenses) {
    return DossierSummary.builder()
        .name(dossier.getName())
        .totalEarnings(allInvoices.stream()
            .filter(i -> dossier.getInvoiceIds().contains(i.getId()))
            .map(InvoiceGeneration::getSubTotal)
            .reduce(new BigDecimal(0), BigDecimal::add))
        .totalExpensesPerTag(allExpenses.stream()
            .filter(e -> dossier.getFeeIds().contains(e.getId()))
            .collect(Collectors.groupingBy(Fee::getTag)))
        .dossier(dossier)
        .build();
  }

  public Optional<Long> getDossierTotalSize(String dossierId) {
    return this.findById(dossierId).map(dossier -> {
      var uploadIds = concat(
          feeService.findAll(dossier.getFeeIds()).stream().flatMap(fee -> fee.getAttachmentIds().stream()),
          concat(invoiceService.findAll(dossier.getInvoiceIds()).stream().map(i -> i.getInvoiceUploadId()),
              documentService.findAll(dossier.getDocumentIds()).stream().map(d -> d.getAttachmentId())))
          .toList();
      return fileUploadService.findAll(uploadIds).stream().mapToLong(u -> u.getSize()).sum();

    });
  }

  public Optional<Dossier> findByFeeId(String id) {
    return this.dossierRepository.findOneByFeeIdsIsContaining(id);
  }

  public Optional<Dossier> getActiveDossier() {
    return dossierRepository.findOneByClosedIsFalse();
  }

  public Page<Dossier> findAll(boolean closed, Pageable pageable) {
    return dossierRepository.findByClosedIs(closed, pageable);
  }

  public List<Dossier> findAll(boolean closed) {
    return dossierRepository.findByClosedOrderByUpdatedDateDesc(closed);
  }

  public List<Dossier> findByClosedIsTrueAndBackupDateIsNull() {
    return dossierRepository.findByClosedIsTrueAndBackupDateIsNull();
  }

  public Optional<Dossier> findById(String dossierId) {
    return this.dossierRepository.findById(dossierId);
  }

  public Dossier update(Dossier dossier) {
    return dossierRepository.save(dossier.toBuilder().updatedDate(new Date()).build());
  }

}
