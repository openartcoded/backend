package tech.artcoded.websitev2.pages.invoice;


import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tech.artcoded.websitev2.api.helper.IdGenerators;
import tech.artcoded.websitev2.notification.NotificationService;
import tech.artcoded.websitev2.rest.annotation.SwaggerHeaderAuthentication;
import tech.artcoded.websitev2.rest.util.MockMultipartFile;
import tech.artcoded.websitev2.upload.FileUploadService;

import javax.inject.Inject;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

@RestController
@RequestMapping("/api/invoice")
@Slf4j
public class InvoiceGenerationController {
  private static final String NOTIFICATION_TYPE = "NEW_INVOICE";
  private final InvoiceGenerationRepository invoiceGenerationRepository;
  private final InvoiceTemplateRepository templateRepository;
  private final FileUploadService fileUploadService;
  private final NotificationService notificationService;
  private final InvoiceToPdfService invoiceToPdfService;
  private final CurrentBillToRepository currentBillToRepository;

  @Inject
  public InvoiceGenerationController(
          InvoiceGenerationRepository invoiceGenerationRepository,
          InvoiceTemplateRepository templateRepository, FileUploadService fileUploadService,
          NotificationService notificationService,
          InvoiceToPdfService invoiceToPdfService, CurrentBillToRepository currentBillToRepository) {
    this.invoiceGenerationRepository = invoiceGenerationRepository;
    this.templateRepository = templateRepository;
    this.fileUploadService = fileUploadService;
    this.notificationService = notificationService;
    this.invoiceToPdfService = invoiceToPdfService;
    this.currentBillToRepository = currentBillToRepository;
  }

  @PostMapping("/new")
  @SwaggerHeaderAuthentication
  public ResponseEntity<InvoiceGeneration> newInvoiceGenerationEmptyTemplate() {
    return getTemplate(
            invoiceGenerationRepository.findByLogicalDeleteIsFalseOrderByDateCreationDesc().stream()
                                       .filter(Predicate.not(InvoiceGeneration::isUploadedManually))
                                       .findFirst());
  }

  @PostMapping("/from-template")
  @SwaggerHeaderAuthentication
  public ResponseEntity<InvoiceGeneration> newInvoiceGenerationFromTemplate(
          @RequestParam("id") String id) {
    return getTemplate(invoiceGenerationRepository.findById(id));
  }

  @DeleteMapping
  @SwaggerHeaderAuthentication
  public ResponseEntity<Map.Entry<String, String>> deleteInvoice(
          @RequestParam("id") String id,
          @RequestParam(value = "logical",
                        defaultValue = "true") boolean logical) {
    if (Boolean.FALSE.equals(logical)) {
      log.warn("invoice {} will be really deleted", id);
      this.invoiceGenerationRepository
              .findById(id)
              .filter(Predicate.not(InvoiceGeneration::isArchived))
              .ifPresent(
                      inv -> {
                        this.fileUploadService.delete(inv.getInvoiceUploadId());
                        this.invoiceGenerationRepository.delete(inv);
                      });
    }
    else {
      log.info("invoice {} will be logically deleted", id);
      this.invoiceGenerationRepository
              .findById(id)
              .map(i -> i.toBuilder().logicalDelete(true).build())
              .ifPresent(invoiceGenerationRepository::save);
    }
    return ResponseEntity.ok(Map.entry("message", "invoice deleted"));
  }

  @PostMapping("/restore")
  public ResponseEntity<Map.Entry<String, String>> restore(@RequestParam("id") String id) {
    this.invoiceGenerationRepository
            .findById(id)
            .filter(InvoiceGeneration::isLogicalDelete)
            .map(i -> i.toBuilder().logicalDelete(false).build())
            .ifPresent(invoiceGenerationRepository::save);
    return ResponseEntity.ok(Map.entry("message", "invoice restored"));

  }

  @PostMapping("/page")
  @SwaggerHeaderAuthentication
  public Page<InvoiceGeneration> page(
          @RequestParam(value = "archived",
                        defaultValue = "false") boolean archived,
          @RequestParam(value = "logical",
                        defaultValue = "false") boolean logicalDelete, Pageable pageable) {
    return invoiceGenerationRepository.findByLogicalDeleteIsAndArchivedIsOrderByDateOfInvoiceDesc(
            logicalDelete, archived, pageable);
  }

  @PostMapping("/find-all")
  @SwaggerHeaderAuthentication
  public List<InvoiceGeneration> findAll(
          @RequestParam(value = "archived",
                        defaultValue = "false") boolean archived,
          @RequestParam(value = "logical",
                        defaultValue = "false") boolean logicalDelete) {
    return invoiceGenerationRepository.findByLogicalDeleteIsAndArchivedIsOrderByDateOfInvoiceDesc(
            logicalDelete, archived);
  }

  @GetMapping("/list-templates")
  @SwaggerHeaderAuthentication
  public List<InvoiceFreemarkerTemplate> listTemplates() {
    return templateRepository.findAll();
  }

  @DeleteMapping("/delete-template")
  @SwaggerHeaderAuthentication
  public void deleteTemplate(@RequestParam("id") String id) {
    this.templateRepository.findById(id).ifPresent(invoiceFreemarkerTemplate -> {
      this.fileUploadService.deleteByCorrelationId(invoiceFreemarkerTemplate.getId());
      this.templateRepository.deleteById(invoiceFreemarkerTemplate.getId());
    });
  }

  @PostMapping(value = "/add-template",
               consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @SwaggerHeaderAuthentication
  public InvoiceFreemarkerTemplate addTemplate(@RequestParam("name") String name,
                                               @RequestPart("template") MultipartFile template) {
    InvoiceFreemarkerTemplate ift = InvoiceFreemarkerTemplate.builder().name(name).build();
    String uploadId = fileUploadService.upload(template, ift.getId(), false);
    return templateRepository.save(ift.toBuilder().templateUploadId(uploadId).build());
  }

  @PostMapping("/find-by-id")
  @SwaggerHeaderAuthentication
  public ResponseEntity<InvoiceGeneration> findById(@RequestParam(value = "id") String id) {
    return invoiceGenerationRepository
            .findById(id)
            .map(ResponseEntity::ok)
            .orElseGet(ResponseEntity.notFound()::build);
  }

  @PostMapping(value = "/manual-upload",
               consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Void> manualUpload(
          @RequestPart("manualUploadFile") MultipartFile file, @RequestParam("id") String id) {
    this.invoiceGenerationRepository
            .findById(id)
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
            .map(invoiceGenerationRepository::save)
            .orElseThrow(() -> new RuntimeException("Invoice not found!!"));
    return ResponseEntity.ok().build();
  }

  @GetMapping("/current-billto")
  public ResponseEntity<CurrentBillTo> currentBillTo() {
    return ResponseEntity.ok(currentBillToRepository.getOrDefault());
  }

  @PostMapping("/current-billto")
  public ResponseEntity<CurrentBillTo> saveCurrentBillTo(@RequestBody CurrentBillTo currentBillTo) {
    CurrentBillTo updated = this.currentBillToRepository.getOrDefault().toBuilder()
                                                        .maxDaysToPay(currentBillTo.getMaxDaysToPay())
                                                        .rate(currentBillTo.getRate())
                                                        .rateType(currentBillTo.getRateType())
                                                        .projectName(currentBillTo.getProjectName())
                                                        .billTo(currentBillTo.getBillTo()).build();
    return ResponseEntity.ok(this.currentBillToRepository.save(updated));
  }

  @PostMapping("/save")
  @SwaggerHeaderAuthentication
  public ResponseEntity<InvoiceGeneration> save(@RequestBody InvoiceGeneration invoiceGeneration) {
    String id = IdGenerators.get();

    InvoiceGeneration partialInvoice =
            invoiceGenerationRepository.save(
                    invoiceGeneration.toBuilder().id(id).locked(true).archived(false).build());

    CompletableFuture.runAsync(
            () -> {
              String pdfId = null;
              if (!invoiceGeneration.isUploadedManually()) {
                pdfId =
                        this.fileUploadService.upload(
                                toMultipart(FilenameUtils.normalize(invoiceGeneration.getInvoiceNumber()), invoiceToPdfService.invoiceToPdf(invoiceGeneration)), id, false);
              }

              InvoiceGeneration invoiceToSave =
                      partialInvoice.toBuilder().invoiceUploadId(pdfId).build();
              InvoiceGeneration saved = invoiceGenerationRepository.save(invoiceToSave);
              this.notificationService.sendEvent(
                      "New Invoice Ready (%s)".formatted(invoiceToSave.getInvoiceNumber()),
                      NOTIFICATION_TYPE, saved.getId());
            });

    return ResponseEntity.ok(partialInvoice);
  }

  private ResponseEntity<InvoiceGeneration> getTemplate(
          Optional<InvoiceGeneration> generationOptional) {
    CurrentBillTo cbt = currentBillToRepository.getOrDefault();
    return generationOptional
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
                             .billTo(ofNullable(i.getBillTo()).orElseGet(cbt::getBillTo))
                             .invoiceTable(
                                     i.getInvoiceTable().stream()
                                      .map(InvoiceRow::toBuilder)
                                      .map(b -> b.period(null).amount(BigDecimal.ZERO).build())
                                      .collect(Collectors.toList()))
                             .dateOfInvoice(new Date())
                             .build())
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.ok(InvoiceGeneration.builder()
                                                                .billTo(cbt.getBillTo())
                                                                .maxDaysToPay(cbt.getMaxDaysToPay())
                                                                .build()));
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
}
