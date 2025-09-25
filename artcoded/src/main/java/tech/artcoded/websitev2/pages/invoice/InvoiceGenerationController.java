package tech.artcoded.websitev2.pages.invoice;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import tech.artcoded.websitev2.peppol.PeppolService;
import tech.artcoded.websitev2.peppol.PeppolStatus;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/invoice")
public class InvoiceGenerationController {
  private final InvoiceService invoiceService;
  private final PeppolService peppolService;

  @Inject
  public InvoiceGenerationController(PeppolService peppolService,
      InvoiceService invoiceService) {
    this.invoiceService = invoiceService;
    this.peppolService = peppolService;
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
      @RequestParam(value = "logical", defaultValue = "true") boolean logical) {
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
      @RequestParam(value = "archived", defaultValue = "false") boolean archived,
      @RequestParam(value = "logical", defaultValue = "false") boolean logicalDelete, Pageable pageable) {
    return invoiceService.page(InvoiceSearchCriteria.builder().archived(archived).logicalDelete(logicalDelete).build(),
        pageable);
  }

  @PostMapping("/find-all-summaries")
  public List<InvoiceSummary> findAllSummaries() {
    return invoiceService.findAllSummaries();

  }

  @GetMapping("/list-templates")
  public List<InvoiceFreemarkerTemplate> listTemplates() {
    return invoiceService.listTemplates();
  }

  @DeleteMapping("/delete-template")
  public void deleteTemplate(@RequestParam("id") String id) {
    this.invoiceService.deleteTemplate(id);

  }

  @PostMapping(value = "/add-template", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public InvoiceFreemarkerTemplate addTemplate(@RequestParam("name") String name,
      @RequestPart("template") MultipartFile template) {
    return this.invoiceService.addTemplate(name, template);
  }

  @PostMapping("/find-by-id")
  public ResponseEntity<InvoiceGeneration> findById(@RequestParam(value = "id") String id) {
    return invoiceService
        .findById(id)
        .map(ResponseEntity::ok)
        .orElseGet(ResponseEntity.notFound()::build);
  }

  @PostMapping("/find-by-ids")
  public ResponseEntity<List<InvoiceGeneration>> findByIds(@RequestParam(value = "id") List<String> ids) {
    return ResponseEntity.ok(invoiceService.findAll(ids));
  }

  @PostMapping("/send-to-peppol")
  public void findByIds(@RequestParam(value = "id") String id) {
    this.invoiceService.findById(id).filter(i -> PeppolStatus.NOT_SENT.equals(i.getPeppolStatus()))
        .ifPresent(i -> peppolService.addInvoice(i));
  }

  @PostMapping(value = "/manual-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
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
