package tech.artcoded.websitev2.pages.dossier;

import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.data.web.SpringDataWebProperties.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;


import tech.artcoded.event.v1.dossier.*;
import tech.artcoded.websitev2.event.ExposedEventService;
import tech.artcoded.websitev2.pages.fee.Fee;
import tech.artcoded.websitev2.pages.fee.FeeService;
import tech.artcoded.websitev2.pages.invoice.InvoiceGeneration;
import tech.artcoded.websitev2.pages.invoice.InvoiceService;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;

@Service
@Slf4j
public class DossierService {
  private final FeeService feeService;
  private final InvoiceService invoiceService;
  private final DossierRepository dossierRepository;

  private final ExposedEventService eventService;
  private final CloseActiveDossierService closeActiveDossierService;

  public DossierService(
    FeeService feeService,
    InvoiceService invoiceService,
    DossierRepository dossierRepository,
    ExposedEventService eventService, CloseActiveDossierService closeActiveDossierService) {
    this.feeService = feeService;
    this.invoiceService = invoiceService;
    this.dossierRepository = dossierRepository;
    this.eventService = eventService;
    this.closeActiveDossierService = closeActiveDossierService;
  }

  public Dossier save(Dossier dossier) {
    return this.dossierRepository.save(dossier);
  }

  public Dossier closeActiveDossier() {
    Dossier dossier = this.closeActiveDossierService.closeActiveDossier();
    eventService.sendEvent(DossierClosed.builder().uploadId(dossier.getDossierUploadId())
      .name(dossier.getName())
      .dossierId(dossier.getId()).build());
    return dossier;
  }

  Dossier closeDossier(Dossier d, Date closedDate) {
    return this.closeActiveDossierService.closeDossier(d, closedDate);
  }

  public void removeFee(String feeId) {
    var optionalDossier = this.getActiveDossier();
    if (optionalDossier.isPresent()) {
      var dossier = optionalDossier.get();
      var optionalFee = feeService
        .findById(feeId)
        .map(
          fee ->
            fee.toBuilder()
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

  public void processInvoiceForDossier(String id) {
    var optionalDossier = this.getActiveDossier();
    optionalDossier.ifPresent(dossier -> processInvoiceForDossier(id, dossier, new Date()));

  }

  public void processFeesForDossier(List<String> feeIds, Dossier dossier, Date date) {
    List<Fee> feesArchived =
      feeIds.stream()
        .map(feeService::findById)
        .flatMap(Optional::stream)
        .filter(f -> f.getTag()!=null && !f.isArchived())
        .toList();
    processFees(feesArchived, dossier, date);
  }

  public Dossier processFees(List<Fee> fees, Dossier dossier, Date date) {
    Set<String> feesArchived =
      fees.stream()
        .map(
          f ->
            f.toBuilder()
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
      return dossier;
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
    Dossier toSave =
      getActiveDossier()
        .map(
          d ->
            d.toBuilder()
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
    var toSave =
      dossierRepository
        .findOneByClosedIsTrueAndIdIs(dossier.getId())
        .map(
          d ->
            d.toBuilder()
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
          i ->
            i.toBuilder()
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

  public DossierSummary getSummary(String id) {
    return findById(id)
      .map(dossier -> DossierSummary.builder()
        .name(dossier.getName())
        .totalEarnings(dossier.getInvoiceIds().stream()
          .map(invoiceService::findById)
          .filter(Optional::isPresent)
          .map(Optional::get)
          .map(InvoiceGeneration::getSubTotal)
          .reduce(new BigDecimal(0), BigDecimal::add)
        )
        .totalExpensesPerTag(dossier.getFeeIds().stream()
          .map(feeService::findById)
          .filter(Optional::isPresent)
          .map(Optional::get)
          .collect(Collectors.groupingBy(Fee::getTag))
        )
        .build()
      ).orElseThrow(() -> new RuntimeException("dossier not found"));
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

