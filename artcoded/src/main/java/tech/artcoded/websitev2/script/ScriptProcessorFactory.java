package tech.artcoded.websitev2.script;

import javax.inject.Inject;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import com.oracle.truffle.js.scriptengine.GraalJSScriptEngine;

import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.pages.client.BillableClientService;
import tech.artcoded.websitev2.pages.document.AdministrativeDocumentService;
import tech.artcoded.websitev2.pages.dossier.DossierService;
import tech.artcoded.websitev2.pages.fee.FeeService;
import tech.artcoded.websitev2.pages.invoice.InvoiceService;
import tech.artcoded.websitev2.pages.personal.PersonalInfoService;
import tech.artcoded.websitev2.pages.timesheet.TimesheetService;
import tech.artcoded.websitev2.rest.util.PdfToolBox;
import tech.artcoded.websitev2.upload.FileUploadService;
import tech.artcoded.websitev2.utils.func.CheckedFunction;
import tech.artcoded.websitev2.utils.service.MailService;

@Service
@Slf4j(topic = "ScriptLogger")
public class ScriptProcessorFactory {

  private final MailService mailService;
  private final FileUploadService fileService;
  private final FeeService feeService;
  private final BillableClientService clientService;
  private final DossierService dossierService;
  private final TimesheetService timesheetService;
  private final InvoiceService invoiceService;
  private final AdministrativeDocumentService documentService;
  private final PersonalInfoService personalInfoService;
  private final MongoTemplate mongoTemplate;

  @Inject
  public ScriptProcessorFactory(MailService mailService, FileUploadService fileService, FeeService feeService,
      BillableClientService clientService, DossierService dossierService, TimesheetService timesheetService,
      InvoiceService invoiceService, AdministrativeDocumentService documentService,
      PersonalInfoService personalInfoService, MongoTemplate mongoTemplate) {
    this.mailService = mailService;
    this.fileService = fileService;
    this.feeService = feeService;
    this.clientService = clientService;
    this.dossierService = dossierService;
    this.timesheetService = timesheetService;
    this.invoiceService = invoiceService;
    this.documentService = documentService;
    this.personalInfoService = personalInfoService;
    this.mongoTemplate = mongoTemplate;
  }

  public GraalJSScriptEngine createScriptEngine() {
    var ctxConfig = Context.newBuilder("js")
        .allowHostAccess(HostAccess.ALL)
        .allowHostClassLookup(s -> true)
        .option("js.ecmascript-version", "2022");
    var engine = GraalJSScriptEngine.create(null, ctxConfig);
    engine.put("mailService", mailService);
    engine.put("fileService", fileService);
    engine.put("feeService", feeService);
    engine.put("clientService", clientService);
    engine.put("dossierService", dossierService);
    engine.put("timesheetService", timesheetService);
    engine.put("invoiceService", invoiceService);
    engine.put("documentService", documentService);
    engine.put("personalInfoService", personalInfoService);
    engine.put("mongoTemplate", mongoTemplate);
    engine.put("generatePdf", CheckedFunction.toFunction(PdfToolBox::generatePDFFromHTML));
    engine.put("logger", log);
    return engine;

  }

}
