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
import tech.artcoded.websitev2.upload.ILinkable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;
import static java.util.stream.Stream.concat;

@Service
public class DossierService implements ILinkable {
    private final FeeService feeService;
    private final InvoiceService invoiceService;
    private final DossierRepository dossierRepository;
    private final AdministrativeDocumentService documentService;
    private final FileUploadService fileUploadService;
    private final ExposedEventService eventService;
    private final CloseActiveDossierService closeActiveDossierService;

    public DossierService(FeeService feeService, InvoiceService invoiceService, DossierRepository dossierRepository,
            FileUploadService fileUploadService, AdministrativeDocumentService documentService,
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

    public Dossier closeActiveDossier() {
        Dossier dossier = this.closeActiveDossierService.closeActiveDossier();
        eventService.sendEvent(DossierClosed.builder().uploadId(dossier.getDossierUploadId()).name(dossier.getName())
                .dossierId(dossier.getId()).build());
        return dossier;
    }

    public Optional<Dossier> toggleBookmarked(String id) {
        return dossierRepository.findById(id)
                .map(dossier -> dossierRepository
                        .save(dossier.toBuilder().updatedDate(new Date()).bookmarked(!dossier.isBookmarked())
                                .bookmarkedDate(dossier.isBookmarked() ? null : new Date()).build()));
    }

    @CacheEvict(cacheNames = { "activeDossier", "dossierSummaries", "dossierTotalSize",
            "dossierByFeeId" }, allEntries = true)
    public Dossier newDossier(Dossier dossier) {
        if (this.getActiveDossier().isEmpty()) {
            Dossier build = Dossier.builder().name(dossier.getName()).tvaDue(dossier.getTvaDue())
                    .advancePayments(dossier.getAdvancePayments()).description(dossier.getDescription()).build();
            Dossier savedDossier = dossierRepository.save(build);
            eventService.sendEvent(
                    DossierCreated.builder().dossierId(savedDossier.getId()).description(savedDossier.getDescription())
                            .tvaDue(savedDossier.getTvaDue()).name(savedDossier.getName()).build());
            return savedDossier;
        } else {
            throw new RuntimeException("You cannot open two dossiers at the same time");
        }
    }

    @CacheEvict(cacheNames = { "activeDossier", "dossierSummaries", "dossierTotalSize",
            "dossierByFeeId" }, allEntries = true)
    public void delete() {
        this.getActiveDossier().filter(d -> !d.isClosed() && d.getInvoiceIds().isEmpty() && d.getFeeIds().isEmpty())
                .ifPresent(dossier -> {
                    this.dossierRepository.delete(dossier);
                    eventService.sendEvent(DossierDeleted.builder().dossierId(dossier.getId()).build());
                });
    }

    @CacheEvict(cacheNames = { "activeDossier" }, allEntries = true)
    public Dossier updateActiveDossier(Dossier dossier) {
        Dossier toSave = getActiveDossier()
                .map(d -> d.toBuilder().name(dossier.getName()).description(dossier.getDescription())
                        .tvaDue(dossier.getTvaDue())
                        .advancePayments(ofNullable(dossier.getAdvancePayments()).orElseGet(List::of))
                        .updatedDate(new Date()).build())
                .orElseThrow(() -> new RuntimeException("No active dossier found"));
        Dossier updated = this.save(toSave);
        eventService.sendEvent(DossierUpdated.builder().dossierId(updated.getId()).tvaDue(dossier.getTvaDue())
                .description(dossier.getDescription()).name(updated.getName()).build());
        return updated;
    }

    /**
     * Only tva can be changed !!!
     *
     * @param dossier
     *            dossier
     *
     * @return the dossier
     */
    @CacheEvict(cacheNames = { "activeDossier", "dossierSummaries", "dossierTotalSize",
            "dossierByFeeId" }, allEntries = true)
    public Dossier recallForModification(Dossier dossier) {
        var toSave = dossierRepository.findOneByClosedIsTrueAndIdIs(dossier.getId())
                .map(d -> d.toBuilder().tvaDue(dossier.getTvaDue())
                        .advancePayments(ofNullable(dossier.getAdvancePayments()).orElseGet(List::of))
                        .updatedDate(new Date()).recalledForModification(true).recalledForModificationDate(new Date())
                        .build())
                .orElseThrow(() -> new RuntimeException("Dossier not found"));
        Dossier save = this.save(toSave);
        this.eventService.sendEvent(
                DossierRecallForModification.builder().dossierId(save.getId()).tvaDue(save.getTvaDue()).build());
        return save;
    }

    public Dossier fromPreviousDossier() {
        var copy = Dossier.builder().id(null).creationDate(null).updatedDate(null);
        return dossierRepository.findFirstByClosedIsTrueOrderByCreationDateDesc()
                .map(d -> copy.name(d.getName() + "(copy)").description(d.getDescription())
                        .advancePayments(d.getAdvancePayments()).bookmarked(false).bookmarkedDate(null).build())
                .orElseGet(copy::build);
    }

    @CachePut(cacheNames = "dossierSummaries", key = "'closedDossierSummaries'", condition = "#closed == true")
    public List<DossierSummary> getAllSummaries(boolean closed) {
        var dossiers = findAll(closed);
        var allInvoiceIds = dossiers.stream().flatMap(d -> d.getInvoiceIds().stream()).toList();
        var allExpenseIds = dossiers.stream().flatMap(d -> d.getFeeIds().stream()).toList();
        var allInvoices = invoiceService.findAll(allInvoiceIds);
        var allExpenses = feeService.findAll(allExpenseIds);
        return findAll(closed).stream().parallel()
                .map(dossier -> this.convertToSummary(dossier, allInvoices, allExpenses)).toList();
    }

    public List<DossierSummary> getSummaries(List<String> ids) {
        var dossiers = dossierRepository.findAllById(ids);
        var allInvoiceIds = dossiers.stream().flatMap(d -> d.getInvoiceIds().stream()).toList();
        var allExpenseIds = dossiers.stream().flatMap(d -> d.getFeeIds().stream()).toList();
        var allInvoices = invoiceService.findAll(allInvoiceIds);
        var allExpenses = feeService.findAll(allExpenseIds);

        return dossierRepository.findAllById(ids).stream().parallel()
                .map(dossier -> this.convertToSummary(dossier, allInvoices, allExpenses)).toList();
    }

    public DossierSummary getSummary(String id) {
        return findById(id).map(dossier -> {
            var allExpenses = feeService.findAll(new ArrayList<>(dossier.getFeeIds()));
            var allInvoices = invoiceService.findAll(new ArrayList<>(dossier.getInvoiceIds()));
            return this.convertToSummary(dossier, allInvoices, allExpenses);
        }).orElseThrow(() -> new RuntimeException("dossier not found"));
    }

    private DossierSummary convertToSummary(Dossier dossier, List<InvoiceGeneration> allInvoices,
            List<Fee> allExpenses) {
        return DossierSummary.builder().name(dossier.getName())
                .totalEarnings(allInvoices.stream().filter(i -> dossier.getInvoiceIds().contains(i.getId()))
                        .map(InvoiceGeneration::getSubTotal).reduce(new BigDecimal(0), BigDecimal::add))
                .totalExpensesPerTag(allExpenses.stream().filter(e -> dossier.getFeeIds().contains(e.getId()))
                        .collect(Collectors.groupingBy(Fee::getTag)))
                .dossier(dossier).build();
    }

    @CachePut(cacheNames = "dossierTotalSize", key = "#dossierId")
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

    @CachePut(cacheNames = "dossierByFeeId", key = "#id")
    public Optional<Dossier> findByFeeId(String id) {
        return this.dossierRepository.findOneByFeeIdsIsContaining(id);
    }

    @CachePut(cacheNames = "activeDossier", key = "'activeDossier'")
    public Optional<Dossier> getActiveDossier() {
        return dossierRepository.findOneByClosedIsFalse();
    }

    public Page<Dossier> findAll(boolean closed, Pageable pageable) {
        return dossierRepository.findByClosedIs(closed, pageable);
    }

    public List<Dossier> findAll(boolean closed) {
        return dossierRepository.findByClosedOrderByUpdatedDateDesc(closed);
    }

    public Page<Dossier> bookmarked(Pageable pageable) {
        return dossierRepository.findByBookmarkedIsOrderByBookmarkedDateDesc(true, pageable);
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

    @Override
    @CachePut(cacheNames = "dossier_correlation_links", key = "#correlationId")
    public String getCorrelationLabel(String correlationId) {
        return this.dossierRepository.findById(correlationId).map(dossier -> toLabel(dossier)).orElse(null);
    }

    private String toLabel(Dossier dossier) {
        return "Dossier '%s' ".formatted(dossier.getName());
    }

    @Override
    @CachePut(cacheNames = "dossier_all_correlation_links", key = "'allLinks'")
    public Map<String, String> getCorrelationLabels(Collection<String> correlationIds) {
        return this.dossierRepository.findAllById(correlationIds).stream()
                .map(doc -> Map.entry(doc.getId(), this.toLabel(doc)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
