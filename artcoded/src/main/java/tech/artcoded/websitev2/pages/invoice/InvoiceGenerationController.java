package tech.artcoded.websitev2.pages.invoice;


import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tech.artcoded.websitev2.notification.NotificationService;
import tech.artcoded.websitev2.upload.FileUploadService;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/invoice")
@Slf4j
public class InvoiceGenerationController {
  private final InvoiceTemplateRepository templateRepository;
  private final FileUploadService fileUploadService;
  private final NotificationService notificationService;
  private final InvoiceService invoiceService;

  @Inject
  public InvoiceGenerationController(
    InvoiceTemplateRepository templateRepository, FileUploadService fileUploadService,
    NotificationService notificationService,
    InvoiceService invoiceService) {
    this.templateRepository = templateRepository;
    this.fileUploadService = fileUploadService;
    this.notificationService = notificationService;
    this.invoiceService = invoiceService;
  }

  @PostMapping("/new")
  public ResponseEntity<InvoiceGeneration> newInvoiceGenerationEmptyTemplate() {
    return ResponseEntity.ok(invoiceService.newInvoiceFromEmptyTemplate());
  }

  @PostMapping("/from-template")
  public ResponseEntity<InvoiceGeneration> newInvoiceGenerationFromTemplate(
    @RequestParam("id") String id) {
    return ResponseEntity.ok(invoiceService.newInvoiceFromExisting(id));
  }

  @DeleteMapping
  public ResponseEntity<Map.Entry<String, String>> deleteInvoice(
    @RequestParam("id") String id,
    @RequestParam(value = "logical",
      defaultValue = "true") boolean logical) {
    invoiceService.delete(id, logical);
    return ResponseEntity.ok(Map.entry("message", "invoice deleted"));
  }

  @PostMapping("/restore")
  public ResponseEntity<Map.Entry<String, String>> restore(@RequestParam("id") String id) {
    this.invoiceService.restore(id);
    return ResponseEntity.ok(Map.entry("message", "invoice restored"));

  }

  @PostMapping("/page")
  public Page<InvoiceGeneration> page(
    @RequestParam(value = "archived",
      defaultValue = "false") boolean archived,
    @RequestParam(value = "logical",
      defaultValue = "false") boolean logicalDelete, Pageable pageable) {
    return invoiceService.page(InvoiceSearchCriteria.builder().archived(archived).logicalDelete(logicalDelete).build(), pageable);
  }

  @PostMapping("/find-all")
  public List<InvoiceGeneration> findAll(
    @RequestParam(value = "archived",
      defaultValue = "false") boolean archived,
    @RequestParam(value = "logical",
      defaultValue = "false") boolean logicalDelete) {
    return invoiceService.findAll(InvoiceSearchCriteria.builder().archived(archived).logicalDelete(logicalDelete).build());

  }

  @GetMapping("/list-templates")
  public List<InvoiceFreemarkerTemplate> listTemplates() {
    return templateRepository.findAll();
  }

  @DeleteMapping("/delete-template")
  public void deleteTemplate(@RequestParam("id") String id) {
    this.templateRepository.findById(id).ifPresent(invoiceFreemarkerTemplate -> {
      this.fileUploadService.deleteByCorrelationId(invoiceFreemarkerTemplate.getId());
      this.templateRepository.deleteById(invoiceFreemarkerTemplate.getId());
    });
  }

  @PostMapping(value = "/add-template",
    consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public InvoiceFreemarkerTemplate addTemplate(@RequestParam("name") String name,
                                               @RequestPart("template") MultipartFile template) {
    InvoiceFreemarkerTemplate ift = InvoiceFreemarkerTemplate.builder().name(name).build();
    String uploadId = fileUploadService.upload(template, ift.getId(), false);
    return templateRepository.save(ift.toBuilder().templateUploadId(uploadId).build());
  }

  @PostMapping("/find-by-id")
  public ResponseEntity<InvoiceGeneration> findById(@RequestParam(value = "id") String id) {
    return invoiceService
      .findById(id)
      .map(ResponseEntity::ok)
      .orElseGet(ResponseEntity.notFound()::build);
  }

  @PostMapping(value = "/manual-upload",
    consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Void> manualUpload(
    @RequestPart("manualUploadFile") MultipartFile file, @RequestParam("id") String id) {
    this.invoiceService.manualUpload(file, id);
    return ResponseEntity.ok().build();
  }

  @PostMapping("/save")
  public ResponseEntity<InvoiceGeneration> save(@RequestBody InvoiceGeneration invoiceGeneration) {


    return ResponseEntity.ok(invoiceService.generateInvoice(invoiceGeneration));
  }

}
