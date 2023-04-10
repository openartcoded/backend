package tech.artcoded.websitev2.pages.invoice;

import freemarker.template.Configuration;
import freemarker.template.Template;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.ProducerTemplate;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tech.artcoded.event.IEvent;
import tech.artcoded.event.v1.invoice.InvoiceGenerated;
import tech.artcoded.event.v1.invoice.InvoiceRemoved;
import tech.artcoded.event.v1.invoice.InvoiceRestored;
import tech.artcoded.websitev2.notification.NotificationService;
import tech.artcoded.websitev2.pages.personal.PersonalInfo;
import tech.artcoded.websitev2.pages.personal.PersonalInfoService;
import tech.artcoded.websitev2.pages.timesheet.TimesheetRepository;
import tech.artcoded.websitev2.pages.timesheet.TimesheetService;
import tech.artcoded.websitev2.rest.util.MockMultipartFile;
import tech.artcoded.websitev2.rest.util.PdfToolBox;
import tech.artcoded.websitev2.upload.FileUploadService;
import tech.artcoded.websitev2.utils.helper.IdGenerators;

import javax.inject.Inject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.net.URLConnection.guessContentTypeFromName;
import static java.util.Optional.ofNullable;
import static org.apache.camel.ExchangePattern.InOnly;
import static org.springframework.ui.freemarker.FreeMarkerTemplateUtils.processTemplateIntoString;
import static tech.artcoded.websitev2.utils.common.Constants.EVENT_PUBLISHER_SEDA_ROUTE;
import static tech.artcoded.websitev2.utils.func.CheckedSupplier.toSupplier;

@Service
@Slf4j
public class InvoiceService {
  private static final String NOTIFICATION_TYPE = "NEW_INVOICE";

  private final PersonalInfoService personalInfoService;
  private final TimesheetRepository timesheetRepository;
  private final InvoiceTemplateRepository templateRepository;
  private final FileUploadService fileUploadService;
  private final InvoiceGenerationRepository repository;
  private final NotificationService notificationService;
  private final ProducerTemplate producerTemplate;

  @Inject
  public InvoiceService(PersonalInfoService personalInfoService,
      InvoiceTemplateRepository templateRepository,
      TimesheetRepository timesheetRepository,
      FileUploadService fileUploadService, InvoiceGenerationRepository repository,
      NotificationService notificationService, ProducerTemplate producerTemplate) {
    this.personalInfoService = personalInfoService;
    this.templateRepository = templateRepository;
    this.fileUploadService = fileUploadService;
    this.repository = repository;
    this.timesheetRepository = timesheetService;
    this.notificationService = notificationService;
    this.producerTemplate = producerTemplate;
  }

  /* make invoice number unique */
  private String generateUniqueInvoiceNumber() {
    var temporaryInvoiceNumber = InvoiceGeneration.generateInvoiceNumber();

    while (repository.existsByInvoiceNumber(temporaryInvoiceNumber)) {
      temporaryInvoiceNumber = InvoiceGeneration.generateInvoiceNumber();
    }
    return temporaryInvoiceNumber;
  }

  @SneakyThrows
  private byte[] invoiceToPdf(InvoiceGeneration ig) {
    PersonalInfo personalInfo = personalInfoService.get();
    String logo = fileUploadService.findOneById(personalInfo.getLogoUploadId())
        .map(file -> Map.of("mediaType", guessContentTypeFromName(file.getOriginalFilename()), "arr",
            fileUploadService.uploadToByteArray(file)))
        .map(map -> "data:%s;base64,%s".formatted(map.get("mediaType"), Base64.getEncoder()
            .encodeToString((byte[]) map.get("arr"))))
        .orElseThrow(() -> new RuntimeException("Could not extract logo from personal info!!!"));
    var data = Map.of("invoice", ig, "personalInfo", personalInfo, "logo", logo);
    String strTemplate = ofNullable(ig.getFreemarkerTemplateId()).flatMap(templateRepository::findById)
        .flatMap(t -> fileUploadService.findOneById(t.getTemplateUploadId()))
        .map(fileUploadService::uploadToInputStream)
        .map(is -> toSupplier(() -> IOUtils.toString(is, StandardCharsets.UTF_8)).get())
        .orElseThrow(() -> new RuntimeException("legacy template missing"));
    Template template = new Template("name", new StringReader(strTemplate),
        new Configuration(Configuration.VERSION_2_3_31));
    String html = toSupplier(() -> processTemplateIntoString(template, data)).get();
    log.debug(html);
    var pdf = PdfToolBox.generatePDFFromHTML(html);
    if (ig.getTimesheetId().isPresent()) {
      try (var baos = new ByteArrayOutputStream();) {
        var mergerUtility = new org.apache.pdfbox.multipdf.PDFMergerUtility();
        var timesheetUploads = this.fileUploadService.findByCorrelationId(false, ig.getTimesheetId().get());

        mergerUtility.addSource(new ByteArrayInputStream(pdf));
        for (var ts : timesheetUploads) {
          if (MediaType.APPLICATION_PDF_VALUE.equals(ts.getContentType())) {
            mergerUtility.addSource(new ByteArrayInputStream(fileUploadService.uploadToByteArray(ts)));
          }

        }
        mergerUtility.setDestinationStream(baos);
        mergerUtility.mergeDocuments(MemoryUsageSetting.setupMixed(8192000));
        pdf = baos.toByteArray();
      } catch (Exception ex) {
        log.error("could not merge invoice with timesheet ", ex);
      }
    }
    return pdf;
  }

  private InvoiceGeneration getTemplate(
      Supplier<Optional<InvoiceGeneration>> invoiceGenerationSupplier) {
    PersonalInfo personalInfo = personalInfoService.get();

    return invoiceGenerationSupplier.get()
        .map(i -> i.toBuilder().invoiceTable(
            i.getInvoiceTable().stream()
                .map(InvoiceRow::toBuilder)
                .map(b -> b.period(null).amount(BigDecimal.ZERO).build())
                .collect(Collectors.toList())))
        .orElseGet(() -> InvoiceGeneration.builder()
            .maxDaysToPay(personalInfo.getMaxDaysToPay())
            .billTo(new BillTo())
            .invoiceTable(List.of(InvoiceRow.builder().period(null).amount(BigDecimal.ZERO).build()))
            .specialNote(""))
        .id(IdGenerators.get())
        .invoiceNumber(generateUniqueInvoiceNumber())
        .locked(false)
        .archived(false)
        .timesheetId(Optional.empty())
        .uploadedManually(false)
        .dateCreation(new Date())
        .updatedDate(null)
        .archivedDate(null)
        .imported(false)
        .specialNote("")
        .invoiceUploadId(null)
        .logicalDelete(false)
        .importedDate(null)
        .dateOfInvoice(new Date())
        .build();
  }

  public void deleteByTimesheetIdAndArchivedIsFalse(String tsId) {
    this.repository.findByTimesheetId(tsId).ifPresent(invoice -> {
      if (invoice.isArchived()) {
        log.info("invoice with id '{}' is locked. cannot delete. remove timesheetId instead", invoice.getId());
        this.repository.save(invoice.toBuilder().updatedDate(new Date()).timesheetId(Optional.empty()).build());
      } else {
        log.info("will delete invoice with id '{}' because timesheet has been deleted", invoice.getId());
        this.delete(invoice.getId(), false);
      }
    });
  }

  public InvoiceGeneration newInvoiceFromEmptyTemplate() {
    return getTemplate(() -> repository.findFirstByLogicalDeleteIsFalseOrderByDateCreationDesc().stream()
        .filter(Predicate.not(InvoiceGeneration::isUploadedManually))
        .findFirst());
  }

  public InvoiceGeneration newInvoiceFromNothing() {
    return getTemplate(() -> Optional.empty());
  }

  public List<InvoiceGeneration> findAll(List<String> ids) {
    Iterable<InvoiceGeneration> it = repository.findAllById(ids);
    List<InvoiceGeneration> results = new ArrayList<>();
    it.forEach(results::add);
    return results;
  }

  public InvoiceGeneration newInvoiceFromExisting(String id) {
    return getTemplate(() -> repository.findById(id));
  }

  public void delete(String id, boolean logical) {
    if (Boolean.FALSE.equals(logical)) {
      log.warn("invoice {} will be really deleted", id);
      this.repository
          .findById(id)
          .filter(Predicate.not(InvoiceGeneration::isArchived))
          .ifPresent(
              inv -> {
                this.fileUploadService.delete(inv.getInvoiceUploadId());
                this.repository.delete(inv);
                inv.getTimesheetId().flatMap(timesheetRepository::findById).ifPresent(ts -> {
                  timesheetRepository.save(ts.toBuilder().invoiceId(Optional.empty()).build());
                });
                sendEvent(InvoiceRemoved.builder()
                    .invoiceId(inv.getId())
                    .uploadId(inv.getInvoiceUploadId())
                    .logicalDelete(false)
                    .build());
              });
    } else {
      log.info("invoice {} will be logically deleted", id);
      this.repository
          .findById(id)
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

  public void restore(String id) {
    this.repository
        .findById(id)
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

  public Page<InvoiceGeneration> page(InvoiceSearchCriteria criteria, Pageable pageable) {
    return repository.findByLogicalDeleteIsAndArchivedIs(
        criteria.isLogicalDelete(), criteria.isArchived(), pageable);
  }

  public List<InvoiceGeneration> findAll(InvoiceSearchCriteria criteria) {
    return repository.findByLogicalDeleteIsAndArchivedIsOrderByDateOfInvoiceDesc(
        criteria.isLogicalDelete(), criteria.isArchived());
  }

  public Optional<InvoiceGeneration> findById(String id) {
    return repository.findById(id);
  }

  public void manualUpload(MultipartFile file, String id) {
    manualUpload(file, id, new Date());
  }

  public void manualUpload(MultipartFile file, String id, Date date) {
    this.findById(id)
        .filter(InvoiceGeneration::isUploadedManually)
        .filter(Predicate.not(InvoiceGeneration::isArchived))
        .filter(Predicate.not(InvoiceGeneration::isLogicalDelete))
        .map(
            invoiceGeneration -> invoiceGeneration.toBuilder()
                .updatedDate(date)
                .invoiceUploadId(
                    this.fileUploadService.upload(file, invoiceGeneration.getId(), false))
                .build())
        .map(repository::save)
        .orElseThrow(() -> new RuntimeException("Invoice not found!!"));
  }

  public InvoiceGeneration generateInvoice(InvoiceGeneration invoiceGeneration) {
    String id = IdGenerators.get();

    log.info("verify invoice number");
    if (StringUtils.isEmpty(invoiceGeneration.getInvoiceNumber())) {
      throw new RuntimeException("invoice number is empty");
    }

    if (repository.existsByInvoiceNumber(invoiceGeneration.getInvoiceNumber())) {
      throw new RuntimeException("invoice %s already exist".formatted(invoiceGeneration.getInvoiceNumber()));
    }

    log.info("invoice number looks valid. proceed...");

    InvoiceGeneration partialInvoice = repository.save(
        invoiceGeneration.toBuilder().id(id).locked(true).archived(false).build());

    Thread.startVirtualThread(
        () -> {
          try {
            String pdfId = null;
            if (!invoiceGeneration.isUploadedManually()) {
              pdfId = this.fileUploadService.upload(
                  toMultipart(FilenameUtils.normalize(invoiceGeneration.getInvoiceNumber()),
                      this.invoiceToPdf(invoiceGeneration)),
                  id, false);
            }
            InvoiceGeneration invoiceToSave = partialInvoice.toBuilder().invoiceUploadId(pdfId).build();
            InvoiceGeneration saved = repository.save(invoiceToSave);
            this.notificationService.sendEvent(
                "New Invoice Ready (%s)".formatted(invoiceToSave.getInvoiceNumber()),
                NOTIFICATION_TYPE, saved.getId());
            sendEvent(InvoiceGenerated.builder()
                .invoiceId(saved.getId())
                .timesheetId(saved.getTimesheetId())
                .subTotal(saved.getSubTotal())
                .taxes(saved.getTaxes())
                .invoiceNumber(saved.getInvoiceNumber())
                .dateOfInvoice(saved.getDateOfInvoice())
                .dueDate(saved.getDueDate())
                .uploadId(saved.getInvoiceUploadId())
                .manualUpload(saved.isUploadedManually())
                .build());
          } catch (Exception e) {
            log.error("something went wrong.", e);
          }
        });
    return partialInvoice;
  }

  private MultipartFile toMultipart(String name, byte[] text) {
    String id = IdGenerators.get();
    var fileName = String.format("%s_%s.pdf", name, id);
    return MockMultipartFile.builder()
        .name(fileName)
        .contentType(MediaType.APPLICATION_PDF_VALUE)
        .originalFilename(fileName)
        .bytes(text)
        .build();
  }

  public InvoiceGeneration update(InvoiceGeneration invoiceGeneration) {
    return repository.save(
        invoiceGeneration.toBuilder()
            .updatedDate(new Date())
            .build());
  }

  private void sendEvent(IEvent event) {
    this.producerTemplate.sendBody(EVENT_PUBLISHER_SEDA_ROUTE, InOnly, event);
  }

  public void deleteTemplate(String id) {
    this.templateRepository.findById(id).ifPresent(invoiceFreemarkerTemplate -> {
      if (this.repository.countByFreemarkerTemplateId(id) != 0) {
        this.templateRepository.save(invoiceFreemarkerTemplate.toBuilder()
            .updatedDate(new Date())
            .logicalDelete(true).build());
      } else {
        this.fileUploadService.deleteByCorrelationId(invoiceFreemarkerTemplate.getId());
        this.templateRepository.deleteById(invoiceFreemarkerTemplate.getId());
      }
    });
  }

  public InvoiceFreemarkerTemplate addTemplate(String name, MultipartFile template) {
    InvoiceFreemarkerTemplate ift = InvoiceFreemarkerTemplate.builder().name(name).build();
    String uploadId = fileUploadService.upload(template, ift.getId(), false);
    return templateRepository.save(ift.toBuilder().templateUploadId(uploadId).build());
  }

  public List<InvoiceFreemarkerTemplate> listTemplates() {
    return templateRepository.findByLogicalDeleteIsFalse();
  }
}
