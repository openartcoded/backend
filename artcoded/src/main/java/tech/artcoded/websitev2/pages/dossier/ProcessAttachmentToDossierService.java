package tech.artcoded.websitev2.pages.dossier;

import org.springframework.stereotype.Service;

import tech.artcoded.event.v1.dossier.*;
import tech.artcoded.websitev2.event.ExposedEventService;
import tech.artcoded.websitev2.pages.document.AdministrativeDocumentService;
import tech.artcoded.websitev2.pages.fee.Fee;
import tech.artcoded.websitev2.pages.fee.FeeService;
import tech.artcoded.websitev2.pages.invoice.InvoiceGeneration;
import tech.artcoded.websitev2.pages.invoice.InvoiceService;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ProcessAttachmentToDossierService {
  private final FeeService feeService;
  private final InvoiceService invoiceService;
  private final DossierRepository dossierRepository;
  private final AdministrativeDocumentService documentService;
  private final ExposedEventService eventService;

  public ProcessAttachmentToDossierService(
      FeeService feeService,
      InvoiceService invoiceService,
      DossierRepository dossierRepository,
      AdministrativeDocumentService documentService,
      ExposedEventService eventService) {
    this.feeService = feeService;
    this.invoiceService = invoiceService;
    this.dossierRepository = dossierRepository;
    this.eventService = eventService;
    this.documentService = documentService;
  }

  public Dossier save(Dossier dossier) {
    return this.dossierRepository.save(dossier);
  }

  public void removeFee(Optional<Dossier> optionalDossier, String feeId) {
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

  public void addDocumentToDossier(Optional<Dossier> optionalDossier, String documentId) {
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

  public void removeDocumentFromDossier(Optional<Dossier> optionalDossier, String documentId) {

    if (optionalDossier.isPresent()) {
      var dossier = optionalDossier.get();
      documentService.unlockDocument(documentId).ifPresent(_ -> {
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

  public void processInvoiceForDossier(Optional<Dossier> optionalDossier, String invoiceId) {
    optionalDossier.ifPresent(dossier -> processInvoiceForDossier(invoiceId, dossier, new Date()));
  }

  public Dossier processFeesForDossier(List<String> feeIds, Dossier dossier, Date date) {
    List<Fee> feesArchived = new HashSet<>(feeIds).stream()
        .map(feeService::findById)
        .flatMap(Optional::stream)
        .filter(f -> f.getTag() != null && !f.isArchived())
        .toList();
    return processFees(feesArchived, dossier, date);
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

  public Optional<Dossier> processFeesForDossier(Optional<Dossier> optionalDossier, List<String> feeIds) {
    return optionalDossier.map(dossier -> processFeesForDossier(feeIds, dossier, new Date()));
  }

  public void removeInvoice(Optional<Dossier> optionalDossier, String invoiceId) {
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

}
