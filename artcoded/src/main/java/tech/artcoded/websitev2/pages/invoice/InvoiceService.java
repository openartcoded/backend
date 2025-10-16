package tech.artcoded.websitev2.pages.invoice;

import static java.net.URLConnection.guessContentTypeFromName;
import static java.util.Optional.ofNullable;
import static org.apache.camel.ExchangePattern.InOnly;
import static org.springframework.ui.freemarker.FreeMarkerTemplateUtils.processTemplateIntoString;
import static tech.artcoded.websitev2.utils.common.Constants.EVENT_PUBLISHER_SEDA_ROUTE;
import static tech.artcoded.websitev2.utils.func.CheckedSupplier.toSupplier;

import freemarker.template.Configuration;
import freemarker.template.Template;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.inject.Inject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.ProducerTemplate;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.core.io.Resource;
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
import tech.artcoded.websitev2.peppol.PeppolStatus;
import tech.artcoded.websitev2.rest.util.MockMultipartFile;
import tech.artcoded.websitev2.rest.util.PdfToolBox;
import tech.artcoded.websitev2.upload.FileUploadService;
import tech.artcoded.websitev2.utils.common.Constants;
import tech.artcoded.websitev2.utils.func.CheckedSupplier;
import tech.artcoded.websitev2.utils.helper.DateHelper;
import tech.artcoded.websitev2.utils.helper.IdGenerators;

@Service
@Slf4j
public class InvoiceService {
  private static final String NOTIFICATION_TYPE = "NEW_INVOICE";

  @Value("classpath:invoice/template-peppol-2025.xml")
  private Resource peppolInvoiceTemplate;

  @Value("classpath:invoice/template-peppol-2025-creditnote.xml")
  private Resource peppolCreditNoteTemplate;
  private final PersonalInfoService personalInfoService;
  private final BillableClientService billableClientService;
  private final TimesheetRepository timesheetRepository;
  private final InvoiceTemplateRepository templateRepository;
  private final FileUploadService fileUploadService;
  private final InvoiceGenerationRepository repository;
  private final NotificationService notificationService;
  private final ProducerTemplate producerTemplate;
  private static final Semaphore SEMAPHORE = new Semaphore(1);

  @Inject
  public InvoiceService(PersonalInfoService personalInfoService,
      InvoiceTemplateRepository templateRepository,
      BillableClientService billableClientService,
      TimesheetRepository timesheetRepository,
      FileUploadService fileUploadService,
      InvoiceGenerationRepository repository,
      NotificationService notificationService,
      ProducerTemplate producerTemplate) {
    this.personalInfoService = personalInfoService;
    this.templateRepository = templateRepository;
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
    var data = Map.of("invoice", ig, "personalInfo", personalInfo, "billableClient", billableClient, "pdfName",
        pdf.getOriginalFilename(), "pdfBytes", Base64.getEncoder().encodeToString(attachment)); // NORDINE
    var templ = ig.isCreditNote() ? peppolCreditNoteTemplate : peppolInvoiceTemplate;
    String strTemplate = CheckedSupplier
        .toSupplier(() -> IOUtils.toString(templ.getInputStream(), StandardCharsets.UTF_8)).get();
    Template template = new Template("name", new StringReader(strTemplate),
        new Configuration(Configuration.VERSION_2_3_31));
    String xml = toSupplier(() -> processTemplateIntoString(template, data)).get();
    log.debug(xml);

    return xml.getBytes(StandardCharsets.UTF_8);
  }

  @SneakyThrows
  private byte[] invoiceToPdf(InvoiceGeneration ig) {
    PersonalInfo personalInfo = personalInfoService.get();
    String logo = fileUploadService.findOneById(personalInfo.getLogoUploadId())
        .map(file -> Map.of("mediaType",
            guessContentTypeFromName(file.getOriginalFilename()),
            "arr", fileUploadService.uploadToByteArray(file)))
        .map(map -> "data:%s;base64,%s".formatted(
            map.get("mediaType"), Base64.getEncoder().encodeToString(
                (byte[]) map.get("arr"))))
        .orElseThrow(
            () -> new RuntimeException(
                "Could not extract logo from personal info!!!"));
    String qrCode = "";
    if (!ig.isCreditNote()) {
      qrCode = "data:%s;base64,%s".formatted(MediaType.IMAGE_PNG_VALUE, Base64.getEncoder()
          .encodeToString(QRCodeUtil.generateBankQRCode(Optional.ofNullable(personalInfo.getOrganizationBankBIC()),
              personalInfo.getOrganizationName(), personalInfo.getOrganizationBankAccount(), ig.getTotal().toString(),
              ig.getStructuredReference())));
    }
    var data = Map.of("invoice", ig, "personalInfo", personalInfo, "logo", logo, "qrCode", qrCode);
    String strTemplate = ofNullable(ig.getFreemarkerTemplateId())
        .flatMap(templateRepository::findById)
        .flatMap(
            t -> fileUploadService.findOneById(t.getTemplateUploadId()))
        .map(fileUploadService::uploadToInputStream)
        .map(is -> toSupplier(
            () -> IOUtils.toString(is, StandardCharsets.UTF_8))
            .get())
        .orElseThrow(
            () -> new RuntimeException(
                "legacy template deprecated. load a template first."));
    Template template = new Template("name", new StringReader(strTemplate),
        new Configuration(Configuration.VERSION_2_3_31));
    String html = toSupplier(() -> processTemplateIntoString(template, data)).get();
    log.debug(html);
    var pdf = PdfToolBox.generatePDFFromHTML(html);
    if (StringUtils.isNotEmpty(ig.getTimesheetId())) {
      try (var baos = new ByteArrayOutputStream();) {
        var mergerUtility = new org.apache.pdfbox.multipdf.PDFMergerUtility();
        var timesheetUploads = this.fileUploadService.findByCorrelationId(
            false, ig.getTimesheetId());
        mergerUtility.addSource(new RandomAccessReadBuffer(pdf));
        for (var ts : timesheetUploads) {
          if (MediaType.APPLICATION_PDF_VALUE.equals(ts.getContentType())) {
            mergerUtility.addSource(new RandomAccessReadBuffer(
                fileUploadService.uploadToByteArray(ts)));
          }
        }
        mergerUtility.setDestinationStream(baos);
        mergerUtility.mergeDocuments(
            org.apache.pdfbox.io.IOUtils.createTempFileOnlyStreamCache());
        pdf = baos.toByteArray();
      } catch (Exception ex) {
        log.error("could not merge invoice with timesheet ", ex);
      }
    }
    return pdf;
  }

  private InvoiceGeneration getTemplate(Supplier<Optional<InvoiceGeneration>> invoiceGenerationSupplier) {
    PersonalInfo personalInfo = personalInfoService.get();

    @SuppressWarnings("deprecation")
    var invoice = invoiceGenerationSupplier.get()
        .map(i -> i.toBuilder().invoiceTable(
            i.getInvoiceTable()
                .stream()
                .map(InvoiceRow::toBuilder)
                .map(b -> b.period(null).amount(BigDecimal.ZERO).build())
                .collect(Collectors.toList())))
        .orElseGet(
            () -> InvoiceGeneration.builder()
                .maxDaysToPay(personalInfo.getMaxDaysToPay())
                .billTo(new BillTo())
                .peppolStatus(PeppolStatus.NOT_SENT)
                .invoiceTable(List.of(InvoiceRow.builder()
                    .period(null)
                    .rateType(RateType.HOURS)
                    .amountType(RateType.HOURS)
                    .rate(BigDecimal.ZERO)
                    .amount(BigDecimal.ZERO)
                    .build()))
                .specialNote(""))
        .id(IdGenerators.get())
        .seqInvoiceNumber(null)
        .invoiceNumber(
            generateUniqueInvoiceNumber()) // now this is just a reference
        .locked(false)
        .archived(false)
        .peppolStatus(PeppolStatus.NOT_SENT)
        .structuredReference(null)
        .creditNoteId(null)
        .timesheetId(null)
        .uploadedManually(false)
        .dateCreation(new Date())
        .updatedDate(null)
        .archivedDate(null)
        .imported(false)
        .specialNote("")
        .creditNoteInvoiceReference(null)
        .invoiceUploadId(null)
        .invoiceUBLId(null)
        .logicalDelete(false)
        .importedDate(null)
        .dateOfInvoice(new Date())
        .build();
    return invoice;
  }

  public void deleteByTimesheetIdAndArchivedIsFalse(String tsId) {
    this.repository.findByTimesheetId(tsId).ifPresent(invoice -> {
      if (invoice.isArchived()) {
        log.info(
            "invoice with id '{}' is locked. cannot delete. remove timesheetId instead",
            invoice.getId());
        this.repository.save(invoice.toBuilder()
            .updatedDate(new Date())
            .timesheetId(null)
            .build());
      } else {
        log.info(
            "will delete invoice with id '{}' because timesheet has been deleted",
            invoice.getId());
        this.delete(invoice.getId(), false);
      }
    });
  }

  public InvoiceGeneration newInvoiceFromEmptyTemplate() {
    return getTemplate(
        () -> repository
            .findFirstByLogicalDeleteIsFalseOrderByDateCreationDesc()
            .stream()
            .filter(Predicate.not(InvoiceGeneration::isUploadedManually))
            .findFirst());
  }

  public InvoiceGeneration newInvoiceFromNothing() {
    return getTemplate(() -> Optional.empty());
  }

  public Optional<InvoiceGeneration> toggleBookmarked(String id) {
    return repository.findById(id)
        .map(i -> repository.save(i.toBuilder().updatedDate(new Date()).bookmarked(!i.isBookmarked())
            .bookmarkedDate(i.isBookmarked() ? null : new Date())
            .build()));
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

  @CachePut(cacheNames = "invoiceSummary", key = "'invSummaries'")
  public List<InvoiceSummary> findAllSummaries() {
    return this
        .findAll(InvoiceSearchCriteria.builder()
            .archived(true)
            .logicalDelete(false)
            .build())
        .stream()
        .map(i -> InvoiceSummary.builder()
            .amountType(i.getInvoiceTable()
                .stream()
                .findFirst()
                .map(t -> t.getAmountType())
                .orElse(null))
            .amount(i.getInvoiceTable()
                .stream()
                .findFirst()
                .map(t -> t.getAmount())
                .orElse(null))
            .period(i.getInvoiceTable()
                .stream()
                .findFirst()
                .map(t -> t.getPeriod())
                .orElse(null))
            .hoursPerDay(i.getInvoiceTable()
                .stream()
                .findFirst()
                .map(t -> t.getHoursPerDay())
                .orElse(null))
            .subTotal(i.getSubTotal())
            .client(i.getClientName())
            .dateOfInvoice(i.getDateOfInvoice())
            .build())
        .toList();
  }

  public InvoiceGeneration newInvoiceFromExisting(String id) {
    return getTemplate(() -> repository.findById(id));
  }

  @CacheEvict(cacheNames = "invoiceSummary", allEntries = true)
  public void delete(String id, boolean logical) {
    if (Boolean.FALSE.equals(logical)) {
      log.warn("invoice {} will be really deleted", id);
      if (repository.countByLogicalDeleteIsOrArchivedIs(true, false) > 1) {
        this.notificationService.sendEvent(
            "cannot delete invoice as there is one unprocessed or more than one logically deleted",
            Constants.NOTIFICATION_SYSTEM_ERROR, id);
        throw new RuntimeException(
            "cannot delete invoice as there is one unprocessed or more than one logically deleted");
      }
      this.repository.findById(id)
          .filter(Predicate.not(InvoiceGeneration::isArchived))
          .ifPresent(inv -> {
            this.fileUploadService.delete(inv.getInvoiceUploadId());
            this.fileUploadService.delete(inv.getInvoiceUBLId());
            this.repository.delete(inv);

            Optional.ofNullable(inv.getTimesheetId())
                .flatMap(timesheetRepository::findById)
                .ifPresent(ts -> {
                  timesheetRepository.save(
                      ts.toBuilder().invoiceId(null).build());
                });
            sendEvent(InvoiceRemoved.builder()
                .invoiceId(inv.getId())
                .uploadId(inv.getInvoiceUploadId())
                .logicalDelete(false)
                .build());
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
      this.repository.findById(id)
          .map(i -> i.toBuilder().logicalDelete(true).build())
          .ifPresent(inv -> {
            repository.save(inv);
            sendEvent(InvoiceRemoved.builder()
                .invoiceId(inv.getId())
                .uploadId(inv.getInvoiceUploadId())
                .logicalDelete(true)
                .build());
          });
    }
  }

  @CacheEvict(cacheNames = "invoiceSummary", allEntries = true)
  public void restore(String id) {
    this.repository.findById(id)
        .filter(InvoiceGeneration::isLogicalDelete)
        .map(i -> i.toBuilder().logicalDelete(false).build())
        .ifPresent(inv -> {
          repository.save(inv);
          sendEvent(InvoiceRestored.builder()
              .invoiceId(inv.getId())
              .uploadId(inv.getInvoiceUploadId())
              .build());
        });
  }

  public Page<InvoiceGeneration> page(InvoiceSearchCriteria criteria,
      Pageable pageable) {
    return repository.findByLogicalDeleteIsAndArchivedIs(
        criteria.isLogicalDelete(), criteria.isArchived(), pageable);
  }

  public List<InvoiceGeneration> findAll(InvoiceSearchCriteria criteria) {
    return repository
        .findByLogicalDeleteIsAndArchivedIsOrderByDateOfInvoiceDesc(
            criteria.isLogicalDelete(), criteria.isArchived());
  }

  public Optional<InvoiceGeneration> findById(String id) {
    return repository.findById(id);
  }

  @CacheEvict(cacheNames = "invoiceSummary", allEntries = true)
  public void manualUpload(MultipartFile file, String id) {
    manualUpload(file, id, new Date());
  }

  public void manualUpload(MultipartFile file, String id, Date date) {
    this.findById(id)
        .filter(InvoiceGeneration::isUploadedManually)
        .filter(Predicate.not(InvoiceGeneration::isArchived))
        .filter(Predicate.not(InvoiceGeneration::isLogicalDelete))
        .map(invoiceGeneration -> invoiceGeneration.toBuilder()
            .updatedDate(date)
            .invoiceUploadId(this.fileUploadService.upload(
                file, invoiceGeneration.getId(), false))
            .build())
        .map(repository::save)
        .orElseThrow(() -> new RuntimeException("Invoice not found!!"));
  }

  @CacheEvict(cacheNames = "invoiceSummary", allEntries = true)
  public InvoiceGeneration makeCreditNote(String id) {
    var invoice = this.repository.findById(id)
        .filter(i -> i.isArchived() && !i.isCreditNote() && !i.isLogicalDelete() && !i.isUploadedManually())
        .orElseThrow(() -> new RuntimeException(
            "invoice with id %s doesn't satisfy rules for making a credit note.".formatted(id)));

    if (!Strings.isBlank(invoice.getCreditNoteId())) {
      throw new RuntimeException("credit note already exists");
    }

    @SuppressWarnings("deprecation")
    var creditNote = this.generateInvoice(invoice.toBuilder()
        .specialNote("Credit Note " + invoice.getNewInvoiceNumber() + " (internal ref: " + invoice.getReference()
            + ", issued date:" + DateHelper.getYYYYMMDD(invoice.getDateOfInvoice()) + ')')
        .creditNoteInvoiceReference(invoice.getNewInvoiceNumber())
        .invoiceUBLId(null)
        .dateOfInvoice(DateHelper.toDate(LocalDate.now()))
        .dateCreation(new Date())
        .updatedDate(null)
        .archivedDate(null)
        .invoiceNumber(InvoiceGeneration.generateInvoiceNumber())
        .structuredReference(InvoiceGeneration.generateStructuredReference(invoice))
        .seqInvoiceNumber(null)
        .invoiceUploadId(null)
        .peppolStatus(PeppolStatus.NOT_SENT)
        .invoiceTable(invoice.getInvoiceTable()
            .stream()
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
      notificationService.sendEvent("an invoice is already being created",
          Constants.NOTIFICATION_SYSTEM_ERROR, id);
      throw new RuntimeException(
          "cannot create an invoice while one is being created");
    }

    if (StringUtils.isEmpty(invoiceGeneration.getReference())) {
      throw new RuntimeException("reference number is empty");
    }
    if (invoiceGeneration.getSeqInvoiceNumber() != null) {
      throw new RuntimeException(
          "seq invoice number should be null at this point");
    }

    if (repository.countByLogicalDeleteIsOrArchivedIs(true, false) != 0) {
      this.notificationService.sendEvent(
          "cannot create a new invoice as there is at least one being processed or logically deleted",
          Constants.NOTIFICATION_SYSTEM_ERROR, id);
      throw new RuntimeException(
          "cannot create a new invoice as there is at least one being processed or logically deleted");
    }

    // TODO this check is probably no longer relevant and not correct at all
    if (repository.existsByInvoiceNumber(
        invoiceGeneration.getReference())) {
      this.notificationService.sendEvent(
          "invoice %s already exist".formatted(
              invoiceGeneration.getReference()),
          Constants.NOTIFICATION_SYSTEM_ERROR, id);
      throw new RuntimeException("invoice %s already exist".formatted(
          invoiceGeneration.getReference()));
    }

    log.info("invoice number looks valid. proceed...");

    InvoiceGeneration partialInvoice = repository.save(invoiceGeneration.toBuilder()
        .id(id)
        .locked(true)
        .structuredReference(InvoiceGeneration.generateStructuredReference(invoiceGeneration))
        .creditNoteId(null)
        .archived(false)
        .build());

    Thread.startVirtualThread(() -> {
      try {
        if (!SEMAPHORE.tryAcquire()) {
          notificationService.sendEvent("could not acquire lock!",
              Constants.NOTIFICATION_SYSTEM_ERROR,
              IdGenerators.get());
          throw new RuntimeException(
              "could not acquire lock for invoice generation!");
        }
        String pdfId = null;
        String ublId = null;
        if (!partialInvoice.isUploadedManually()) {
          pdfId = this.fileUploadService.upload(
              toMultipart(
                  FilenameUtils.normalize(partialInvoice.getNewInvoiceNumber()),
                  this.invoiceToPdf(partialInvoice), "pdf", MediaType.APPLICATION_PDF_VALUE),
              id, false);
          ublId = this.fileUploadService.upload(
              toMultipart(
                  FilenameUtils.normalize(partialInvoice.getNewInvoiceNumber()),
                  this.invoiceToUBL(partialInvoice, pdfId), "xml", MediaType.TEXT_XML_VALUE),
              id, false);
        }
        InvoiceGeneration invoiceToSave = partialInvoice.toBuilder()
            .invoiceUploadId(pdfId)
            .peppolStatus(PeppolStatus.NOT_SENT)
            .invoiceUBLId(ublId)
            .build();
        InvoiceGeneration saved = repository.save(invoiceToSave);
        this.notificationService.sendEvent(
            "New Invoice Ready (%s)".formatted(
                invoiceToSave.getNewInvoiceNumber()),
            NOTIFICATION_TYPE, saved.getId());
        sendEvent(InvoiceGenerated.builder()
            .invoiceId(saved.getId())
            .bankReference(saved.getStructuredReference())
            .creditNoteReference(saved.getCreditNoteInvoiceReference())
            .timesheetId(saved.getTimesheetId())
            .subTotal(saved.getSubTotal())
            .taxes(saved.getTaxes())
            .ublId(saved.getInvoiceUBLId())
            .seq(saved.getSeqInvoiceNumber())
            .invoiceNumber(saved.getNewInvoiceNumber())
            .referenceNumber(saved.getReference())
            .dateOfInvoice(saved.getDateOfInvoice())
            .dueDate(saved.getDueDate())
            .uploadId(saved.getInvoiceUploadId())
            .manualUpload(saved.isUploadedManually())
            .build());
      } catch (Exception e) {
        log.error("something went wrong.", e);
        notificationService.sendEvent("could not create invoice, check logs",
            NOTIFICATION_TYPE, IdGenerators.get());
      } finally {
        SEMAPHORE.release();
      }
    });
    return partialInvoice;
  }

  private MultipartFile toMultipart(String name, byte[] text, String extension, String contentType) {
    String id = IdGenerators.get();
    var fileName = String.format("%s_%s.%s", name, id, extension);
    return MockMultipartFile.builder()
        .name(fileName)
        .contentType(contentType)
        .originalFilename(fileName)
        .bytes(text)
        .build();
  }

  public InvoiceGeneration update(InvoiceGeneration invoiceGeneration) {
    return repository.save(
        invoiceGeneration.toBuilder().updatedDate(new Date()).build());
  }

  private void sendEvent(IEvent event) {
    this.producerTemplate.sendBody(EVENT_PUBLISHER_SEDA_ROUTE, InOnly, event);
  }

  public void deleteTemplate(String id) {
    this.templateRepository.findById(id).ifPresent(
        invoiceFreemarkerTemplate -> {
          if (this.repository.countByFreemarkerTemplateId(id) != 0) {
            this.templateRepository.save(invoiceFreemarkerTemplate.toBuilder()
                .updatedDate(new Date())
                .logicalDelete(true)
                .build());
          } else {
            this.fileUploadService.deleteByCorrelationId(
                invoiceFreemarkerTemplate.getId());
            this.templateRepository.deleteById(
                invoiceFreemarkerTemplate.getId());
          }
        });
  }

  public InvoiceFreemarkerTemplate addTemplate(String name,
      MultipartFile template) {
    InvoiceFreemarkerTemplate ift = InvoiceFreemarkerTemplate.builder().name(name).build();
    String uploadId = fileUploadService.upload(template, ift.getId(), false);
    return templateRepository.save(
        ift.toBuilder().templateUploadId(uploadId).build());
  }

  public List<InvoiceFreemarkerTemplate> listTemplates() {
    return templateRepository.findByLogicalDeleteIsFalse();
  }
}
