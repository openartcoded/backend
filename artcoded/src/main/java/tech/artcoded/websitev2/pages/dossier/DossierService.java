package tech.artcoded.websitev2.pages.dossier;

import lombok.extern.slf4j.Slf4j;
import org.apache.camel.ProducerTemplate;
import org.springframework.stereotype.Service;
import tech.artcoded.websitev2.event.dto.*;
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

import static org.apache.camel.ExchangePattern.InOnly;
import static tech.artcoded.websitev2.api.common.Constants.EVENT_PUBLISHER_SEDA_ROUTE;

@Service
@Slf4j
public class DossierService {
  private final FeeService feeService;
  private final InvoiceService invoiceService;
  private final DossierRepository dossierRepository;

  private final ProducerTemplate producerTemplate;
  private final CloseActiveDossierService closeActiveDossierService;

  public DossierService(
    FeeService feeService,
    InvoiceService invoiceService,
    DossierRepository dossierRepository,
    ProducerTemplate producerTemplate, CloseActiveDossierService closeActiveDossierService) {
    this.feeService = feeService;
    this.invoiceService = invoiceService;
    this.dossierRepository = dossierRepository;
    this.producerTemplate = producerTemplate;
    this.closeActiveDossierService = closeActiveDossierService;
  }

  public List<Dossier> findAll(boolean closed) {
    return dossierRepository.findByClosedOrderByUpdatedDateDesc(closed);
  }

  public Dossier save(Dossier dossier) {
    return this.dossierRepository.save(dossier);
  }

  public Dossier closeActiveDossier() {
    return this.closeActiveDossierService.closeActiveDossier();
  }

  public List<Dossier> findByClosedIsTrueAndBackupDateIsNull() {
    return dossierRepository.findByClosedIsTrueAndBackupDateIsNull();
  }

  public Optional<Dossier> findById(String dossierId) {
    return this.dossierRepository.findById(dossierId);
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

        this.producerTemplate.sendBody(EVENT_PUBLISHER_SEDA_ROUTE, InOnly, ExpenseRemovedFromDossier.builder()
          .dossierId(dossier.getId()).expenseRemovedId(fee.getId()).build());

      }
    }

  }

  public void processInvoiceForDossier(String id) {
    var optionalDossier = this.getActiveDossier();
    if (optionalDossier.isPresent()) {
      var dossier = optionalDossier.get();
      var optionalInvoice = invoiceService
        .findById(id)
        .filter(i -> !i.isArchived())
        .map(
          i -> i.toBuilder()
            .archived(true)
            .archivedDate(new Date())
            .build())
        .map(invoiceService::update);
      if (optionalInvoice.isPresent()) {
        var invoice = optionalInvoice.get();
        dossierRepository.save(
          dossier.toBuilder()
            .invoiceIds(
              Stream.concat(
                  Stream.of(invoice.getId()),
                  dossier.getInvoiceIds().stream())
                .collect(Collectors.toSet()))
            .updatedDate(new Date())
            .build());

        this.producerTemplate.sendBody(EVENT_PUBLISHER_SEDA_ROUTE, InOnly, InvoiceAddedToDossier.builder()
          .dossierId(dossier.getId()).invoiceId(invoice.getId()).build());
      }

    }

  }

  public void processFeesForDossier(List<String> feeIds) {
    var optionalDossier = this.getActiveDossier();
    if (optionalDossier.isPresent()) {
      var dossier = optionalDossier.get();
      Set<String> feesArchived =
        feeIds.stream()
          .map(feeService::findById)
          .flatMap(Optional::stream)
          .filter(f -> f.getTag()!=null && !f.isArchived())
          .map(
            f ->
              f.toBuilder()
                .archived(true)
                .updatedDate(new Date())
                .archivedDate(new Date())
                .build())
          .map(feeService::update)
          .map(Fee::getId)
          .collect(Collectors.toSet());

      dossierRepository.save(
        dossier.toBuilder()
          .feeIds(
            Stream.concat(dossier.getFeeIds().stream(), feesArchived.stream())
              .collect(Collectors.toSet()))
          .updatedDate(new Date())
          .build());

      this.producerTemplate.sendBody(EVENT_PUBLISHER_SEDA_ROUTE, InOnly, ExpensesAddedToDossier.builder()
        .dossierId(dossier.getId()).addedExpenseIds(feesArchived).build());
    }

  }

  public Dossier newDossier(Dossier dossier) {
    if (this.getActiveDossier().isEmpty()) {
      Dossier build = Dossier.builder().name(dossier.getName())
        .tvaDue(dossier.getTvaDue())
        .advancePayments(dossier.getAdvancePayments())
        .description(dossier.getDescription()).build();
      Dossier savedDossier = dossierRepository.save(build);
      producerTemplate.sendBody(EVENT_PUBLISHER_SEDA_ROUTE, InOnly, DossierCreated.builder()
        .dossierId(savedDossier.getId()).name(savedDossier.getName()).build());
      return dossier;
    } else {
      throw new RuntimeException("You cannot open two dossiers at the same time");
    }
  }

  public void delete() {
    this.getActiveDossier()
      .filter(d -> !d.isClosed() && d.getInvoiceIds().isEmpty() && d.getFeeIds().isEmpty())
      .ifPresent(this.dossierRepository::delete);
  }

  public Optional<Dossier> findByFeeId(String id) {
    return this.dossierRepository.findOneByFeeIdsIsContaining(id);
  }

  public Optional<Dossier> getActiveDossier() {
    return dossierRepository.findOneByClosedIsFalse();
  }

  public Dossier updateDossier(Dossier dossier) {
    Dossier toSave =
      getActiveDossier()
        .map(
          d ->
            d.toBuilder()
              .name(dossier.getName())
              .description(dossier.getDescription())
              .tvaDue(dossier.getTvaDue())
              .advancePayments(
                Optional.ofNullable(dossier.getAdvancePayments()).orElseGet(List::of))
              .updatedDate(new Date())
              .build())
        .orElseThrow(() -> new RuntimeException("No active dossier found"));
    return this.save(toSave);
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
              .advancePayments(
                Optional.ofNullable(dossier.getAdvancePayments()).orElseGet(List::of))
              .updatedDate(new Date())
              .recalledForModification(true)
              .recalledForModificationDate(new Date())
              .build())
        .orElseThrow(() -> new RuntimeException("Dossier not found"));
    return this.save(toSave);
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

        this.producerTemplate.sendBody(EVENT_PUBLISHER_SEDA_ROUTE, InOnly, InvoiceRemovedFromDossier.builder()
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

}

