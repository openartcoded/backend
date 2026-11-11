package tech.artcoded.websitev2.pages.invoice;

import static java.net.URLConnection.guessContentTypeFromName;
import static java.util.Optional.ofNullable;
import static org.apache.camel.ExchangePattern.InOnly;
import static tech.artcoded.websitev2.utils.common.Constants.EVENT_PUBLISHER_SEDA_ROUTE;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.ProducerTemplate;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import tech.artcoded.event.IEvent;
import tech.artcoded.event.v1.invoice.InvoiceGenerated;
import tech.artcoded.event.v1.invoice.InvoiceRemoved;
import tech.artcoded.event.v1.invoice.InvoiceRestored;
import tech.artcoded.websitev2.domain.common.RateType;
import tech.artcoded.websitev2.notification.NotificationService;
import tech.artcoded.websitev2.pages.client.BillableClient;
import tech.artcoded.websitev2.pages.client.BillableClientService;
import tech.artcoded.websitev2.pages.personal.PersonalInfo;
import tech.artcoded.websitev2.pages.personal.PersonalInfoService;
import tech.artcoded.websitev2.pages.timesheet.TimesheetRepository;
import tech.artcoded.websitev2.peppol.PeppolAttachments;
import tech.artcoded.websitev2.peppol.PeppolStatus;
import tech.artcoded.websitev2.rest.util.MockMultipartFile;
import tech.artcoded.websitev2.template.OaTemplate;
import tech.artcoded.websitev2.template.OaTemplateRenderer;
import tech.artcoded.websitev2.template.OaTemplateService;
import tech.artcoded.websitev2.template.OaTemplateType;
import tech.artcoded.websitev2.upload.FileUpload;
import tech.artcoded.websitev2.upload.IFileUploadService;
import tech.artcoded.websitev2.upload.ILinkable;
import tech.artcoded.websitev2.utils.common.Constants;
import tech.artcoded.websitev2.utils.helper.DateHelper;
import tech.artcoded.websitev2.utils.helper.IdGenerators;

@Service
@Slf4j
public class InvoiceService implements ILinkable {
    private static final String NOTIFICATION_TYPE = "NEW_INVOICE";

    private final PersonalInfoService personalInfoService;
    private final BillableClientService billableClientService;
    private final TimesheetRepository timesheetRepository;
    private final OaTemplateService templateService;
    private final OaTemplateRenderer templateRenderer;
    private final IFileUploadService fileUploadService;
    private final InvoiceGenerationRepository repository;
    private final NotificationService notificationService;
    private final ProducerTemplate producerTemplate;
    /** One invoice generation at a time. Package-private so tests can wait for a generation to fully complete. */
    static final Semaphore SEMAPHORE = new Semaphore(1);

    public InvoiceService(PersonalInfoService personalInfoService, OaTemplateService templateService,
            OaTemplateRenderer templateRenderer, BillableClientService billableClientService,
            TimesheetRepository timesheetRepository, IFileUploadService fileUploadService,
            InvoiceGenerationRepository repository, NotificationService notificationService,
            ProducerTemplate producerTemplate) {
        this.personalInfoService = personalInfoService;
        this.templateService = templateService;
        this.templateRenderer = templateRenderer;
        this.billableClientService = billableClientService;
        this.fileUploadService = fileUploadService;
        this.repository = repository;
        this.timesheetRepository = timesheetRepository;
        this.notificationService = notificationService;
        this.producerTemplate = producerTemplate;
    }

    @Deprecated
    private String generateUniqueInvoiceNumber() {
        var temporaryInvoiceNumber = InvoiceGeneration.generateInvoiceNumber();

        while (repository.existsByInvoiceNumber(temporaryInvoiceNumber)) {
            temporaryInvoiceNumber = InvoiceGeneration.generateInvoiceNumber();
        }
        return temporaryInvoiceNumber;
    }

    @SneakyThrows
    private byte[] invoiceToUBL(InvoiceGeneration ig, String pdfId) {
        PersonalInfo personalInfo = personalInfoService.get();
        BillableClient billableClient = billableClientService.findOneByCompanyNumber(ig.getBillTo().getCompanyNumber())
                .orElseThrow(() -> new RuntimeException(
                        "client with company number %s doesn't exist. therefore cannot generate the ubl invoice"
                                .formatted(ig.getBillTo().getCompanyNumber())));
        var pdf = fileUploadService.findOneById(pdfId).orElseThrow(() -> new RuntimeException("pdf doesnt exist"));
        var attachment = fileUploadService.uploadToByteArray(pdf);
        Map<String, Object> data = Map.of("invoice", ig, "personalInfo", personalInfo, "billableClient", billableClient,
                "pdfName", pdf.getOriginalFilename(), "pdfBytes", Base64.getEncoder().encodeToString(attachment)); // NORDINE
        var type = ig.isCreditNote() ? OaTemplateType.PEPPOL_CREDIT_NOTE : OaTemplateType.PEPPOL_INVOICE;
        var ubl = templateRenderer.renderDefault(type, data, "peppol-%s".formatted(ig.getId()));
        return PeppolAttachments.addAttachments(ubl, extraAttachments(ig));
    }

    /** the extra files of the invoice, in the order they were added */
    private List<PeppolAttachments.Attachment> extraAttachments(InvoiceGeneration ig) {
        var ids = attachmentIds(ig);
        if (ids.isEmpty()) {
            return List.of();
        }
        var byId = fileUploadService.findAll(ids).stream()
                .collect(Collectors.toMap(FileUpload::getId, Function.identity(), (a, b) -> a));
        return ids.stream().map(byId::get).filter(Objects::nonNull)
                .map(f -> new PeppolAttachments.Attachment(f.getOriginalFilename(), f.getContentType(),
                        fileUploadService.uploadToByteArray(f)))
                .toList();
    }

    static List<String> attachmentIds(InvoiceGeneration ig) {
        return Optional.ofNullable(ig.getAttachmentIds()).orElse(List.of()).stream().filter(StringUtils::isNotBlank)
                .distinct().toList();
    }

    /**
     * the extra files must exist and, when the invoice goes to Peppol, be of a type Peppol accepts. Checked before
     * anything is stored: the generation itself runs in the background.
     */
    void validateAttachments(InvoiceGeneration ig) {
        var ids = attachmentIds(ig);
        if (ids.isEmpty()) {
            return;
        }
        var files = fileUploadService.findAll(ids);
        if (files.size() != ids.size()) {
            var found = files.stream().map(FileUpload::getId).collect(Collectors.toSet());
            throw new RuntimeException("attachment(s) not found: %s"
                    .formatted(ids.stream().filter(Predicate.not(found::contains)).toList()));
        }
        if (!ig.isSkipPeppol() && !ig.isUploadedManually()) {
            var refused = files.stream().filter(f -> !PeppolAttachments.isAllowed(f.getContentType()))
                    .map(FileUpload::getOriginalFilename).toList();
            if (!refused.isEmpty()) {
                throw new RuntimeException("Peppol only accepts pdf, png, jpeg, csv, xlsx and ods attachments, not: %s"
                        .formatted(refused));
            }
        }
    }

    @SneakyThrows
    private byte[] invoiceToPdf(InvoiceGeneration ig) {
        PersonalInfo personalInfo = personalInfoService.get();
        String logo = fileUploadService.findOneById(personalInfo.getLogoUploadId())
                .map(file -> Map.of("mediaType", guessContentTypeFromName(file.getOriginalFilename()), "arr",
                        fileUploadService.uploadToByteArray(file)))
                .map(map -> "data:%s;base64,%s".formatted(map.get("mediaType"),
                        Base64.getEncoder().encodeToString((byte[]) map.get("arr"))))
                .orElseThrow(() -> new RuntimeException("Could not extract logo from personal info!!!"));
        String qrCode = "";
        if (!ig.isCreditNote()) {
            qrCode = "data:%s;base64,%s".formatted(MediaType.IMAGE_PNG_VALUE,
                    Base64.getEncoder()
                            .encodeToString(QRCodeUtil.generateBankQRCode(
                                    Optional.ofNullable(personalInfo.getOrganizationBankBIC()),
                                    personalInfo.getOrganizationName(), personalInfo.getOrganizationBankAccount(),
                                    ig.getTotal().toString(), ig.getStructuredReference())));
        }
        Map<String, Object> data = Map.of("invoice", ig, "personalInfo", personalInfo, "logo", logo, "qrCode", qrCode);
        // the template chosen on the invoice (the default when it was created), otherwise the default one
        var fileName = "invoice-%s".formatted(ig.getId());
        var pdf = templateService.findById(ig.getFreemarkerTemplateId())
                .filter(t -> t.getType() == OaTemplateType.INVOICE).map(t -> templateRenderer.render(t, data, fileName))
                .orElseGet(() -> templateRenderer.renderDefault(OaTemplateType.INVOICE, data, fileName));
        if (StringUtils.isNotEmpty(ig.getTimesheetId())) {
            try (var baos = new ByteArrayOutputStream();) {
                var mergerUtility = new org.apache.pdfbox.multipdf.PDFMergerUtility();
                var timesheetUploads = this.fileUploadService.findByCorrelationId(false, ig.getTimesheetId());
                mergerUtility.addSource(new RandomAccessReadBuffer(pdf));
                for (var ts : timesheetUploads) {
                    if (MediaType.APPLICATION_PDF_VALUE.equals(ts.getContentType())) {
                        mergerUtility.addSource(new RandomAccessReadBuffer(fileUploadService.uploadToByteArray(ts)));
                    }
                }
                mergerUtility.setDestinationStream(baos);
                mergerUtility.mergeDocuments(org.apache.pdfbox.io.IOUtils.createTempFileOnlyStreamCache());
                pdf = baos.toByteArray();
            } catch (Exception ex) {
                log.error("could not merge invoice with timesheet ", ex);
            }
        }
        return pdf;
    }

    /**
     * Number of rows (products) a new invoice created from the UI starts with.
     */
    static final int DEFAULT_NUMBER_OF_ROWS = 1;

    /**
     * Makes sure the given rows contain at least {@code minRows} entries. Missing rows are copies of the last known row
     * (same template), so they can be adjusted independently (e.g. each with its own rate). When there is no row at
     * all, an empty row is used as the template.
     */
    static List<InvoiceRow> ensureMinimumRows(List<InvoiceRow> rows, int minRows) {
        var result = new ArrayList<>(rows);
        if (result.isEmpty() && minRows > 0) {
            result.add(InvoiceRow.builder().period(null).rateType(RateType.HOURS).amountType(RateType.HOURS)
                    .rate(BigDecimal.ZERO).amount(BigDecimal.ZERO).build());
        }
        while (result.size() < minRows) {
            result.add(result.getLast().toBuilder().build());
        }
        return result;
    }

    private InvoiceGeneration getTemplate(Supplier<Optional<InvoiceGeneration>> invoiceGenerationSupplier,
            int minRows) {
        PersonalInfo personalInfo = personalInfoService.get();

        // 2025-10-16 22:16
        // Most of the time, we expect to use the latest invoice template,
        // this will hopefully reduces mistakes
        // the default template, when one is set, wins over the latest one
        var latestTemplate = defaultTemplate();

        @SuppressWarnings("deprecation")
        var invoice = invoiceGenerationSupplier
                .get().map(
                        i -> i.toBuilder()
                                .freemarkerTemplateId(latestTemplate.map(
                                        t -> t.getId()).orElse(
                                                i.getFreemarkerTemplateId()))
                                .invoiceTable(ensureMinimumRows(i.getInvoiceTable().stream().map(InvoiceRow::toBuilder)
                                        .map(b -> b.period(null).amount(BigDecimal.ZERO).build())
                                        .collect(Collectors.toList()), minRows)))
                .orElseGet(() -> InvoiceGeneration.builder().maxDaysToPay(personalInfo.getMaxDaysToPay())
                        .billTo(new BillTo()).freemarkerTemplateId(latestTemplate.map(t -> t.getId()).orElse(null))
                        .peppolStatus(PeppolStatus.NOT_SENT).invoiceTable(ensureMinimumRows(List.of(), minRows))
                        .specialNote(""))
                .id(IdGenerators.get()).seqInvoiceNumber(null).invoiceNumber(generateUniqueInvoiceNumber()) // now this
                                                                                                            // is just a
                                                                                                            // reference
                .locked(false).archived(false).peppolStatus(PeppolStatus.NOT_SENT).bookmarked(false)
                .bookmarkedDate(null).structuredReference(null).creditNoteId(null).timesheetId(null)
                .uploadedManually(false).dateCreation(new Date()).updatedDate(null).archivedDate(null).imported(false)
                .skipPeppol(false).specialNote("").creditNoteInvoiceReference(null).invoiceUploadId(null)
                .invoiceUBLId(null).logicalDelete(false).importedDate(null).dateOfInvoice(new Date())
                .attachmentIds(new ArrayList<>()).build();
        return invoice;
    }

    public void deleteByTimesheetIdAndArchivedIsFalse(String tsId) {

        this.repository.findByTimesheetId(tsId).ifPresent(invoice -> {
            if (invoice.isArchived()) {
                log.info("invoice with id '{}' is locked. cannot delete. remove timesheetId instead", invoice.getId());
                this.repository.save(invoice.toBuilder().updatedDate(new Date()).timesheetId(null).build());
            } else {
                log.info("will delete invoice with id '{}' because timesheet has been deleted", invoice.getId());
                this.delete(invoice.getId(), false);
            }
            this.timesheetRepository.findById(tsId).ifPresent(ts -> {
                timesheetRepository.save(ts.toBuilder().invoiceId(null).build());
            });
        });

    }

    public InvoiceGeneration newInvoiceFromEmptyTemplate() {
        return getTemplate(
                () -> repository.findFirstByLogicalDeleteIsFalseOrderByDateCreationDesc().stream()
                        .filter(Predicate.not(InvoiceGeneration::isUploadedManually)).findFirst(),
                DEFAULT_NUMBER_OF_ROWS);
    }

    public InvoiceGeneration newInvoiceFromNothing() {
        // used by the timesheet flow, which fills exactly one row
        return getTemplate(() -> Optional.empty(), 1);
    }

    public Optional<InvoiceGeneration> toggleBookmarked(String id) {
        return repository.findById(id).map(i -> repository.save(i.toBuilder().updatedDate(new Date())
                .bookmarked(!i.isBookmarked()).bookmarkedDate(i.isBookmarked() ? null : new Date()).build()));
    }

    public Page<InvoiceGeneration> getBookmarked(Pageable pageable) {
        return repository.findByBookmarkedIsOrderByBookmarkedDateDesc(true, pageable);
    }

    public List<InvoiceGeneration> findAll(Collection<String> ids) {
        var it = repository.findAllById(ids);
        // FIXME this seems dumb but it could be related to an odd bug with the graal-js
        // maybe I should make some tests without this nonsense
        List<InvoiceGeneration> results = new ArrayList<>();
        it.forEach(results::add);
        return results;
    }

    private InvoiceSummary mapSummary(InvoiceGeneration i) {
        return InvoiceSummary.builder()
                .amountType(i.getInvoiceTable().stream().findFirst().map(t -> t.getAmountType()).orElse(null))
                .amount(i.getInvoiceTable().stream().findFirst().map(t -> t.getAmount()).orElse(null))
                .period(i.getInvoiceTable().stream().findFirst().map(t -> t.getPeriod()).orElse(null))
                .hoursPerDay(i.getInvoiceTable().stream().findFirst().map(t -> t.getHoursPerDay()).orElse(null))
                .subTotal(i.getSubTotal()).client(i.getClientName()).dateOfInvoice(i.getDateOfInvoice()).build();
    }

    @CachePut(cacheNames = "invoiceSummary", key = "'invSummaries'")
    public List<InvoiceSummary> findAllSummaries() {
        return this.repository.findAll().stream().map(this::mapSummary).toList();
    }

    public InvoiceGeneration newInvoiceFromExisting(String id) {
        return getTemplate(() -> repository.findById(id), DEFAULT_NUMBER_OF_ROWS);
    }

    @CacheEvict(cacheNames = "invoiceSummary", allEntries = true)
    public void delete(String id, boolean logical) {
        timesheetRepository.findByInvoiceId(id).ifPresent(ts -> {
            this.timesheetRepository.save(ts.toBuilder().invoiceId(null).build());
        });
        if (Boolean.FALSE.equals(logical)) {
            log.warn("invoice {} will be really deleted", id);
            if (repository.countByLogicalDeleteIsOrArchivedIs(true, false) > 1) {
                this.notificationService.sendEvent(
                        "cannot delete invoice as there is one unprocessed or more than one logically deleted",
                        Constants.NOTIFICATION_SYSTEM_ERROR, id);
                throw new RuntimeException(
                        "cannot delete invoice as there is one unprocessed or more than one logically deleted");
            }
            this.repository.findById(id).filter(Predicate.not(InvoiceGeneration::isArchived)).ifPresent(inv -> {
                this.fileUploadService.delete(inv.getInvoiceUploadId());
                this.fileUploadService.delete(inv.getInvoiceUBLId());
                // the extra files go with the invoice (only when it is really deleted, a logical delete keeps them)
                attachmentIds(inv).forEach(this.fileUploadService::delete);

                this.repository.delete(inv);

                Optional.ofNullable(inv.getTimesheetId()).flatMap(timesheetRepository::findById).ifPresent(ts -> {
                    timesheetRepository.save(ts.toBuilder().invoiceId(null).build());
                });
                sendEvent(InvoiceRemoved.builder().invoiceId(inv.getId()).uploadId(inv.getInvoiceUploadId())
                        .logicalDelete(false).build());
            });
        } else {
            log.info("invoice {} will be logically deleted", id);
            if (repository.countByLogicalDeleteIsOrArchivedIs(true, false) > 1) {
                this.notificationService.sendEvent(
                        "cannot delete invoice logically as there is one unprocessed or more than one logically deleted",
                        Constants.NOTIFICATION_SYSTEM_ERROR, id);
                throw new RuntimeException(
                        "cannot delete invoice logically as there is one unprocessed or more than one logically deleted");
            }
            this.repository.findById(id).map(i -> i.toBuilder().logicalDelete(true).build()).ifPresent(inv -> {
                repository.save(inv);
                sendEvent(InvoiceRemoved.builder().invoiceId(inv.getId()).uploadId(inv.getInvoiceUploadId())
                        .logicalDelete(true).build());
            });
        }
    }

    @CacheEvict(cacheNames = "invoiceSummary", allEntries = true)
    public void restore(String id) {
        this.repository.findById(id).filter(InvoiceGeneration::isLogicalDelete)
                .map(i -> i.toBuilder().logicalDelete(false).build()).ifPresent(inv -> {
                    repository.save(inv);
                    sendEvent(InvoiceRestored.builder().invoiceId(inv.getId()).uploadId(inv.getInvoiceUploadId())
                            .build());
                });
    }

    public Page<InvoiceGeneration> page(InvoiceSearchCriteria criteria, Pageable pageable) {
        return repository.findByLogicalDeleteIsAndArchivedIs(criteria.isLogicalDelete(), criteria.isArchived(),
                pageable);
    }

    public List<InvoiceGeneration> findAll(InvoiceSearchCriteria criteria) {
        return repository.findByLogicalDeleteIsAndArchivedIsOrderByDateOfInvoiceDesc(criteria.isLogicalDelete(),
                criteria.isArchived());
    }

    public Optional<InvoiceGeneration> findById(String id) {
        return repository.findById(id);
    }

    @CacheEvict(cacheNames = "invoiceSummary", allEntries = true)
    public void manualUpload(MultipartFile file, String id) {
        manualUpload(file, id, new Date());
    }

    public void manualUpload(MultipartFile file, String id, Date date) {
        this.findById(id).filter(InvoiceGeneration::isUploadedManually)
                .filter(Predicate.not(InvoiceGeneration::isArchived))
                .filter(Predicate.not(InvoiceGeneration::isLogicalDelete))
                .map(invoiceGeneration -> invoiceGeneration.toBuilder().updatedDate(date).skipPeppol(true)
                        .invoiceUploadId(this.fileUploadService.upload(file, invoiceGeneration.getId(), false)).build())
                .map(repository::save).orElseThrow(() -> new RuntimeException("Invoice not found!!"));
    }

    @CacheEvict(cacheNames = "invoiceSummary", allEntries = true)
    public InvoiceGeneration makeCreditNote(String id) {
        var invoice = this.repository.findById(id)
                .filter(i -> i.isArchived() && !i.isCreditNote() && !i.isLogicalDelete() && !i.isUploadedManually())
                .orElseThrow(() -> new RuntimeException(
                        "invoice with id %s doesn't satisfy rules for making a credit note.".formatted(id)));

        if (StringUtils.isNotBlank(invoice.getCreditNoteId())) {
            throw new RuntimeException("credit note already exists");
        }

        @SuppressWarnings("deprecation")
        var creditNote = this
                .generateInvoice(invoice.toBuilder()
                        .specialNote("Credit Note " + invoice.getNewInvoiceNumber() + " (internal ref: "
                                + invoice.getReference() + ", issued date:"
                                + DateHelper.getDD_MM_YYYY(invoice.getDateOfInvoice()) + ')')
                        .creditNoteInvoiceReference(invoice.getNewInvoiceNumber()).invoiceUBLId(null)
                        .attachmentIds(new ArrayList<>()).dateOfInvoice(DateHelper.toDate(LocalDate.now()))
                        .dateCreation(new Date()).updatedDate(null).archivedDate(null)
                        .invoiceNumber(InvoiceGeneration.generateInvoiceNumber())
                        .structuredReference(InvoiceGeneration.generateStructuredReference(invoice))
                        .seqInvoiceNumber(null).invoiceUploadId(null).peppolStatus(PeppolStatus.NOT_SENT)
                        .invoiceTable(invoice.getInvoiceTable().stream()
                                .map(line -> line.toBuilder().amount(line.getAmount().negate()).build()).toList())
                        .build());

        this.repository.save(invoice.toBuilder().updatedDate(new Date()).creditNoteId(creditNote.getId()).build());

        return creditNote;
    }

    @CacheEvict(cacheNames = "invoiceSummary", allEntries = true)
    public InvoiceGeneration generateInvoice(InvoiceGeneration invoiceGeneration) {
        String id = IdGenerators.get();

        log.info("verify invoice number");
        if (SEMAPHORE.availablePermits() == 0) {
            notificationService.sendEvent("an invoice is already being created", Constants.NOTIFICATION_SYSTEM_ERROR,
                    id);
            throw new RuntimeException("cannot create an invoice while one is being created");
        }

        if (StringUtils.isEmpty(invoiceGeneration.getReference())) {
            throw new RuntimeException("reference number is empty");
        }
        if (invoiceGeneration.getSeqInvoiceNumber() != null) {
            throw new RuntimeException("seq invoice number should be null at this point");
        }

        if (repository.countByLogicalDeleteIsOrArchivedIs(true, false) != 0) {
            this.notificationService.sendEvent(
                    "cannot create a new invoice as there is at least one being processed or logically deleted",
                    Constants.NOTIFICATION_SYSTEM_ERROR, id);
            throw new RuntimeException(
                    "cannot create a new invoice as there is at least one being processed or logically deleted");
        }

        // TODO this check is probably no longer relevant and not correct at all
        if (repository.existsByInvoiceNumber(invoiceGeneration.getReference())) {
            this.notificationService.sendEvent("invoice %s already exist".formatted(invoiceGeneration.getReference()),
                    Constants.NOTIFICATION_SYSTEM_ERROR, id);
            throw new RuntimeException("invoice %s already exist".formatted(invoiceGeneration.getReference()));
        }

        validateAttachments(invoiceGeneration);

        log.info("invoice number looks valid. proceed...");

        InvoiceGeneration partialInvoice = repository.save(invoiceGeneration.toBuilder().id(id).locked(true)
                .attachmentIds(new ArrayList<>(attachmentIds(invoiceGeneration)))
                .structuredReference(InvoiceGeneration.generateStructuredReference(invoiceGeneration))
                .creditNoteId(null).archived(false).build());

        Thread.startVirtualThread(() -> {
            var acquired = false;
            try {
                acquired = SEMAPHORE.tryAcquire();
                if (!acquired) {
                    notificationService.sendEvent("could not acquire lock!", Constants.NOTIFICATION_SYSTEM_ERROR,
                            IdGenerators.get());
                    throw new RuntimeException("could not acquire lock for invoice generation!");
                }
                String pdfId = null;
                String ublId = null;
                var peppolStatus = PeppolStatus.OLD;
                if (!partialInvoice.isUploadedManually()) {
                    pdfId = this.fileUploadService.upload(
                            toMultipart(FilenameUtils.normalize(partialInvoice.getNewInvoiceNumber()),
                                    this.invoiceToPdf(partialInvoice), "pdf", MediaType.APPLICATION_PDF_VALUE),
                            id, false);
                    if (!partialInvoice.isSkipPeppol()) {
                        ublId = this.fileUploadService.upload(
                                toMultipart(FilenameUtils.normalize(partialInvoice.getNewInvoiceNumber()),
                                        this.invoiceToUBL(partialInvoice, pdfId), "xml", MediaType.TEXT_XML_VALUE),
                                id, false);
                        peppolStatus = PeppolStatus.NOT_SENT;
                    }

                }
                InvoiceGeneration invoiceToSave = partialInvoice.toBuilder().invoiceUploadId(pdfId)
                        .peppolStatus(peppolStatus).invoiceUBLId(ublId).build();
                InvoiceGeneration saved = repository.save(invoiceToSave);
                this.notificationService.sendEvent(
                        "New Invoice Ready (%s)".formatted(invoiceToSave.getNewInvoiceNumber()), NOTIFICATION_TYPE,
                        saved.getId());
                sendEvent(InvoiceGenerated.builder().invoiceId(saved.getId())
                        .bankReference(saved.getStructuredReference())
                        .creditNoteReference(saved.getCreditNoteInvoiceReference()).timesheetId(saved.getTimesheetId())
                        .subTotal(saved.getSubTotal()).taxes(saved.getTaxes()).ublId(saved.getInvoiceUBLId())
                        .seq(saved.getSeqInvoiceNumber()).invoiceNumber(saved.getNewInvoiceNumber())
                        .referenceNumber(saved.getReference()).dateOfInvoice(saved.getDateOfInvoice())
                        .dueDate(saved.getDueDate()).uploadId(saved.getInvoiceUploadId())
                        .manualUpload(saved.isUploadedManually()).build());
            } catch (Exception e) {
                log.error("something went wrong.", e);
                notificationService.sendEvent("could not create invoice, check logs", NOTIFICATION_TYPE,
                        IdGenerators.get());
            } finally {
                // only give back a permit we actually own: releasing unconditionally used to add an extra permit
                // each time the lock could not be acquired, silently allowing concurrent invoice generations
                if (acquired) {
                    SEMAPHORE.release();
                }
            }
        });
        return partialInvoice;
    }

    /**
     * Changes the extra files of an invoice that is already generated (locked or processed). When its Peppol XML is not
     * sent yet, the XML is generated again with the new files. Otherwise (sent, or no Peppol), only the list is stored:
     * it is used for the mail and the files are deleted with the invoice. Removed files are only detached.
     */
    @CacheEvict(cacheNames = "invoiceSummary", allEntries = true)
    public InvoiceGeneration updateAttachments(String id, List<String> attachmentIds) {
        var invoice = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("invoice %s not found".formatted(id)));
        if (invoice.isLogicalDelete()) {
            throw new RuntimeException("invoice %s is deleted".formatted(id));
        }
        if (!invoice.isUploadedManually() && StringUtils.isBlank(invoice.getInvoiceUploadId())) {
            throw new RuntimeException("invoice %s is still being generated, try again in a moment".formatted(id));
        }
        var regenerateUbl = invoice.getPeppolStatus() == PeppolStatus.NOT_SENT
                && StringUtils.isNotBlank(invoice.getInvoiceUBLId());
        var ids = attachmentIds(InvoiceGeneration.builder().attachmentIds(attachmentIds).build());
        // the peppol types only matter when the files go into an xml that is still to be sent
        validateAttachments(invoice.toBuilder().attachmentIds(ids).skipPeppol(!regenerateUbl).build());
        var updated = invoice.toBuilder().attachmentIds(new ArrayList<>(ids)).updatedDate(new Date()).build();
        if (!regenerateUbl) {
            return repository.save(updated);
        }
        var oldUblId = invoice.getInvoiceUBLId();
        var ublId = this.fileUploadService.upload(
                toMultipart(FilenameUtils.normalize(updated.getNewInvoiceNumber()),
                        this.invoiceToUBL(updated, updated.getInvoiceUploadId()), "xml", MediaType.TEXT_XML_VALUE),
                updated.getId(), false);
        var saved = repository.save(updated.toBuilder().invoiceUBLId(ublId).build());
        this.fileUploadService.delete(oldUblId);
        return saved;
    }

    private MultipartFile toMultipart(String name, byte[] text, String extension, String contentType) {
        String id = IdGenerators.get();
        var fileName = String.format("%s_%s.%s", name, id, extension);
        return MockMultipartFile.builder().name(fileName).contentType(contentType).originalFilename(fileName)
                .bytes(text).build();
    }

    public InvoiceGeneration update(InvoiceGeneration invoiceGeneration) {
        return repository.save(invoiceGeneration.toBuilder().updatedDate(new Date()).build());
    }

    private void sendEvent(IEvent event) {
        this.producerTemplate.sendBody(EVENT_PUBLISHER_SEDA_ROUTE, InOnly, event);
    }

    /** kept for the current frontend: templates are now managed in /api/template */
    public void deleteTemplate(String id) {
        templateService.findById(id).filter(t -> t.getType() == OaTemplateType.INVOICE)
                .ifPresent(t -> templateService.delete(t.getId()));
    }

    /** The template new invoices use: the default invoice template. */
    public Optional<OaTemplate> defaultTemplate() {
        return templateService.getDefault(OaTemplateType.INVOICE);
    }

    /**
     * kept for the current frontend: templates are now managed in /api/template. A type always has a default: it can
     * only be replaced by another one.
     */
    public Optional<OaTemplate> setDefaultTemplate(String id, boolean defaultTemplate) {
        if (!defaultTemplate) {
            throw new IllegalArgumentException("there is always a default template: make another template the default");
        }
        return templateService.findById(id).filter(t -> t.getType() == OaTemplateType.INVOICE)
                .flatMap(t -> templateService.setDefault(t.getId()));
    }

    /**
     * kept for the current frontend (FreeMarker uploads): templates are now managed in /api/template
     */
    public OaTemplate addTemplate(String name, MultipartFile template) {
        return templateService.save(null, name, null, OaTemplateType.INVOICE, true, false, template);
    }

    public List<OaTemplate> listTemplates() {
        return templateService.list(Optional.of(OaTemplateType.INVOICE));
    }

    @Override
    @CachePut(cacheNames = "invoice_correlation_links", key = "#correlationId")
    public String getCorrelationLabel(String correlationId) {
        return this.findById(correlationId).map(toLabel()).orElse(null);
    }

    private Function<? super InvoiceGeneration, ? extends String> toLabel() {
        return invoice -> "Invoice N° %s".formatted(invoice.getNewInvoiceNumber());
    }

    @Override
    @CachePut(cacheNames = "invoice_all_correlation_links", key = "'allLinks'")
    public Map<String, String> getCorrelationLabels(Collection<String> correlationIds) {
        return this.repository.findAllById(correlationIds).stream()
                .map(f -> Map.entry(f.getId(), this.toLabel().apply(f)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public void updateOldId(String correlationId, String oldId, String newId) {
        this.repository.findById(correlationId).ifPresent(p -> {
            var changed = false;
            if (oldId.equals(p.getInvoiceUBLId())) {
                p.setInvoiceUBLId(newId);
                changed = true;
            }
            if (oldId.equals(p.getInvoiceUploadId())) {
                p.setInvoiceUploadId(newId);
                changed = true;
            }
            if (p.getAttachmentIds() != null && p.getAttachmentIds().contains(oldId)) {
                p.setAttachmentIds(new ArrayList<>(p.getAttachmentIds().stream()
                        .map(attachmentId -> oldId.equals(attachmentId) ? newId : attachmentId).toList()));
                changed = true;
            }
            if (changed) {
                this.repository.save(p.toBuilder().updatedDate(new Date()).build());
            }
        });

    }
}
