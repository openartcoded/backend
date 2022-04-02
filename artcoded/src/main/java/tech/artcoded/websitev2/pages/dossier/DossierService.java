package tech.artcoded.websitev2.pages.dossier;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tech.artcoded.websitev2.pages.fee.Fee;
import tech.artcoded.websitev2.pages.fee.FeeRepository;
import tech.artcoded.websitev2.pages.invoice.InvoiceGeneration;
import tech.artcoded.websitev2.pages.invoice.InvoiceGenerationRepository;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
public class DossierService {
  private final FeeRepository feeRepository;
  private final InvoiceGenerationRepository invoiceGenerationRepository;
  private final DossierRepository dossierRepository;
  private final CloseActiveDossierService closeActiveDossierService;

  public DossierService(
          FeeRepository feeRepository,
          InvoiceGenerationRepository invoiceGenerationRepository,
          DossierRepository dossierRepository,
          CloseActiveDossierService closeActiveDossierService) {
    this.feeRepository = feeRepository;
    this.invoiceGenerationRepository = invoiceGenerationRepository;
    this.dossierRepository = dossierRepository;
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

  public List<Dossier> findByClosedIsTrueAndBackupDateIsNull(){
    return dossierRepository.findByClosedIsTrueAndBackupDateIsNull();
  }

  public Optional<Dossier> findById(String dossierId) {
    return this.dossierRepository.findById(dossierId);
  }

  public void removeFee(String feeId) {
    this.getActiveDossier()
        .ifPresent(
                dossier -> {
                  feeRepository
                          .findById(feeId)
                          .map(
                                  fee ->
                                          fee.toBuilder()
                                             .archived(false)
                                             .archivedDate(null)
                                             .updatedDate(new Date())
                                             .build())
                          .ifPresent(feeRepository::save);
                  this.save(
                          dossier.toBuilder()
                                 .updatedDate(new Date())
                                 .feeIds(
                                         dossier.getFeeIds().stream()
                                                .filter(id -> !id.equals(feeId))
                                                .collect(Collectors.toSet()))
                                 .build());
                });
  }

  public void processInvoiceForDossier(String id) {

    this.getActiveDossier()
        .ifPresent(
                dossier -> {
                  invoiceGenerationRepository
                          .findById(id)
                          .filter(i -> !i.isArchived())
                          .map(
                                  i ->
                                          i.toBuilder()
                                           .archived(true)
                                           .updatedDate(new Date())
                                           .archivedDate(new Date())
                                           .build())
                          .map(invoiceGenerationRepository::save)
                          .ifPresent(
                                  invoiceGeneration ->
                                          dossierRepository.save(
                                                  dossier.toBuilder()
                                                         .invoiceIds(
                                                                 Stream.concat(
                                                                               Stream.of(invoiceGeneration.getId()),
                                                                               dossier.getInvoiceIds().stream())
                                                                       .collect(Collectors.toSet()))
                                                         .updatedDate(new Date())
                                                         .build()));
                });
  }

  public void processFeesForDossier(List<String> feeIds) {

    this.getActiveDossier()
        .ifPresent(
                dossier -> {
                  Set<String> feesArchived =
                          feeIds.stream()
                                .map(feeRepository::findById)
                                .flatMap(Optional::stream)
                                .filter(f -> f.getTag() != null && !f.isArchived())
                                .map(
                                        f ->
                                                f.toBuilder()
                                                 .archived(true)
                                                 .updatedDate(new Date())
                                                 .archivedDate(new Date())
                                                 .build())
                                .map(feeRepository::save)
                                .map(Fee::getId)
                                .collect(Collectors.toSet());
                  dossierRepository.save(
                          dossier.toBuilder()
                                 .feeIds(
                                         Stream.concat(dossier.getFeeIds().stream(), feesArchived.stream())
                                               .collect(Collectors.toSet()))
                                 .updatedDate(new Date())
                                 .build());
                });
  }

  public Dossier newDossier(Dossier dossier) {
    if (this.getActiveDossier().isEmpty()) {
      Dossier build = Dossier.builder().name(dossier.getName())
                             .tvaDue(dossier.getTvaDue())
                             .advancePayments(dossier.getAdvancePayments())
                             .description(dossier.getDescription()).build();
      return dossierRepository.save(build);
    }
    else {
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
   * @param dossier
   * @return
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
    this.getActiveDossier()
        .ifPresent(
                dossier -> {
                  invoiceGenerationRepository
                          .findById(invoiceId)
                          .map(
                                  i ->
                                          i.toBuilder()
                                           .archived(false)
                                           .archivedDate(null)
                                           .updatedDate(new Date())
                                           .build())
                          .ifPresent(invoiceGenerationRepository::save);
                  this.save(
                          dossier.toBuilder()
                                 .updatedDate(new Date())
                                 .invoiceIds(
                                         dossier.getInvoiceIds().stream()
                                                .filter(id -> !id.equals(invoiceId))
                                                .collect(Collectors.toSet()))
                                 .build());
                });
  }

  public DossierSummary getSummary(String id) {
    return findById(id)
            .map(dossier -> DossierSummary.builder()
                                          .name(dossier.getName())
                                          .totalEarnings(dossier.getInvoiceIds().stream()
                                                                .map(invoiceGenerationRepository::findById)
                                                                .filter(Optional::isPresent)
                                                                .map(Optional::get)
                                                                .map(InvoiceGeneration::getSubTotal)
                                                                .reduce(new BigDecimal(0), BigDecimal::add)
                                          )
                                          .totalExpensesPerTag(dossier.getFeeIds().stream()
                                                                      .map(feeRepository::findById)
                                                                      .filter(Optional::isPresent)
                                                                      .map(Optional::get)
                                                                      .collect(Collectors.groupingBy(Fee::getTag))
                                          )
                                          .build()
            ).orElseThrow(() -> new RuntimeException("dossier not found"));
  }

}

