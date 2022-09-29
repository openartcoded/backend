package tech.artcoded.websitev2.pages.invoice;

import freemarker.template.Configuration;
import freemarker.template.Template;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.ProducerTemplate;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tech.artcoded.event.IEvent;
import tech.artcoded.event.v1.invoice.InvoiceGenerated;
import tech.artcoded.event.v1.invoice.InvoiceRemoved;
import tech.artcoded.event.v1.invoice.InvoiceRestored;
import tech.artcoded.websitev2.api.helper.IdGenerators;
import tech.artcoded.websitev2.notification.NotificationService;
import tech.artcoded.websitev2.pages.personal.PersonalInfo;
import tech.artcoded.websitev2.pages.personal.PersonalInfoService;
import tech.artcoded.websitev2.rest.util.MockMultipartFile;
import tech.artcoded.websitev2.rest.util.PdfToolBox;
import tech.artcoded.websitev2.upload.FileUploadService;

import javax.inject.Inject;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.net.URLConnection.guessContentTypeFromName;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.CompletableFuture.runAsync;
import static org.apache.camel.ExchangePattern.InOnly;
import static org.springframework.ui.freemarker.FreeMarkerTemplateUtils.processTemplateIntoString;
import static tech.artcoded.websitev2.api.common.Constants.EVENT_PUBLISHER_SEDA_ROUTE;
import static tech.artcoded.websitev2.api.func.CheckedSupplier.toSupplier;

@Service
@Slf4j
public class InvoiceService {
  private static final String NOTIFICATION_TYPE = "NEW_INVOICE";

  private final PersonalInfoService personalInfoService;
  private final InvoiceTemplateRepository templateRepository;
  private final FileUploadService fileUploadService;
  private final InvoiceGenerationRepository repository;
  private final NotificationService notificationService;
  private final ProducerTemplate producerTemplate;

  @Inject
  public InvoiceService(PersonalInfoService personalInfoService,
                        InvoiceTemplateRepository templateRepository,
                        FileUploadService fileUploadService, InvoiceGenerationRepository repository, NotificationService notificationService, ProducerTemplate producerTemplate) {
    this.personalInfoService = personalInfoService;
    this.templateRepository = templateRepository;
    this.fileUploadService = fileUploadService;
    this.repository = repository;
    this.notificationService = notificationService;
    this.producerTemplate = producerTemplate;
  }

  @SneakyThrows
  private byte[] invoiceToPdf(InvoiceGeneration ig) {
    PersonalInfo personalInfo = personalInfoService.get();
    String logo = fileUploadService.findOneById(personalInfo.getLogoUploadId())
      .map(file -> Map.of("mediaType", guessContentTypeFromName(file.getOriginalFilename()), "arr", fileUploadService.uploadToByteArray(file)))
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
    return PdfToolBox.generatePDFFromHTML(html);
  }


  private InvoiceGeneration getTemplate(
    Supplier<Optional<InvoiceGeneration>> invoiceGenerationSupplier) {
    PersonalInfo personalInfo = personalInfoService.get();

    return invoiceGenerationSupplier.get()
      .map(
        i ->
          i.toBuilder()
            .id(IdGenerators.get())
            .invoiceNumber(InvoiceGeneration.generateInvoiceNumber())
            .locked(false)
            .archived(false)
            .uploadedManually(false)
            .invoiceUploadId(null)
            .logicalDelete(false)
            .billTo(ofNullable(i.getBillTo()).orElseGet(BillTo::new))
            .invoiceTable(
              i.getInvoiceTable().stream()
                .map(InvoiceRow::toBuilder)
                .map(b -> b.period(null).amount(BigDecimal.ZERO).build())
                .collect(Collectors.toList()))
            .dateOfInvoice(new Date())
            .build())
      .orElseGet(() -> InvoiceGeneration.builder()
        .billTo(new BillTo())
        .maxDaysToPay(personalInfo.getMaxDaysToPay())
        .build());
  }

  public InvoiceGeneration newInvoiceFromEmptyTemplate() {
    return getTemplate(() ->
      repository.findByLogicalDeleteIsFalseOrderByDateCreationDesc().stream()
        .filter(Predicate.not(InvoiceGeneration::isUploadedManually))
        .findFirst());
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
    return repository.findByLogicalDeleteIsAndArchivedIsOrderByDateOfInvoiceDesc(
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
    this.findById(id)
      .filter(InvoiceGeneration::isUploadedManually)
      .filter(Predicate.not(InvoiceGeneration::isArchived))
      .filter(Predicate.not(InvoiceGeneration::isLogicalDelete))
      .map(
        invoiceGeneration ->
          invoiceGeneration.toBuilder()
            .updatedDate(new Date())
            .invoiceUploadId(
              this.fileUploadService.upload(file, invoiceGeneration.getId(), false))
            .build())
      .map(repository::save)
      .orElseThrow(() -> new RuntimeException("Invoice not found!!"));
  }

  public InvoiceGeneration generateInvoice(InvoiceGeneration invoiceGeneration) {
    String id = IdGenerators.get();

    InvoiceGeneration partialInvoice =
      repository.save(
        invoiceGeneration.toBuilder().id(id).locked(true).archived(false).build());

    runAsync(
      () -> {
        try {
          String pdfId = null;
          if (!invoiceGeneration.isUploadedManually()) {
            pdfId =
              this.fileUploadService.upload(
                toMultipart(FilenameUtils.normalize(invoiceGeneration.getInvoiceNumber()), this.invoiceToPdf(invoiceGeneration)), id, false);
          }
          InvoiceGeneration invoiceToSave =
            partialInvoice.toBuilder().invoiceUploadId(pdfId).build();
          InvoiceGeneration saved = repository.save(invoiceToSave);
          this.notificationService.sendEvent(
            "New Invoice Ready (%s)".formatted(invoiceToSave.getInvoiceNumber()),
            NOTIFICATION_TYPE, saved.getId());
          sendEvent(InvoiceGenerated.builder()
            .invoiceId(saved.getId())
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
        .build()
    );
  }

  private void sendEvent(IEvent event) {
    this.producerTemplate.sendBody(EVENT_PUBLISHER_SEDA_ROUTE, InOnly, event);
  }
}
