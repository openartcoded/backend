package tech.artcoded.websitev2.script;

import javax.inject.Inject;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import com.oracle.truffle.js.runtime.JSContextOptions;

import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.notification.NotificationService;
import tech.artcoded.websitev2.pages.client.BillableClientService;
import tech.artcoded.websitev2.pages.cv.service.CurriculumService;
import tech.artcoded.websitev2.pages.document.AdministrativeDocumentService;
import tech.artcoded.websitev2.pages.dossier.DossierService;
import tech.artcoded.websitev2.pages.fee.FeeService;
import tech.artcoded.websitev2.pages.invoice.InvoiceService;
import tech.artcoded.websitev2.pages.personal.PersonalInfoService;
import tech.artcoded.websitev2.pages.task.ReminderTaskService;
import tech.artcoded.websitev2.pages.timesheet.TimesheetService;
import tech.artcoded.websitev2.rest.util.PdfToolBox;
import tech.artcoded.websitev2.upload.FileUploadService;
import tech.artcoded.websitev2.utils.common.LogOutputStream;
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
  private final ReminderTaskService reminderTaskService;
  private final AdministrativeDocumentService documentService;
  private final PersonalInfoService personalInfoService;
  private final MongoTemplate mongoTemplate;
  private final NotificationService notificationService;
  private final CurriculumService curriculumService;

  @Inject
  public ScriptProcessorFactory(MailService mailService, FileUploadService fileService, FeeService feeService,
      NotificationService notificationService,
      ReminderTaskService reminderTaskService,
      CurriculumService curriculumService,
      BillableClientService clientService, DossierService dossierService, TimesheetService timesheetService,
      InvoiceService invoiceService, AdministrativeDocumentService documentService,
      PersonalInfoService personalInfoService, MongoTemplate mongoTemplate) {
    this.mailService = mailService;
    this.fileService = fileService;
    this.notificationService = notificationService;
    this.feeService = feeService;
    this.curriculumService = curriculumService;
    this.clientService = clientService;
    this.reminderTaskService = reminderTaskService;
    this.dossierService = dossierService;
    this.timesheetService = timesheetService;
    this.invoiceService = invoiceService;
    this.documentService = documentService;
    this.personalInfoService = personalInfoService;
    this.mongoTemplate = mongoTemplate;
  }

  public Context createContext() {
    var ctxConfig = Context.newBuilder("js")
        .allowHostAccess(HostAccess.ALL)
        .out(new LogOutputStream(log))
        .err(new LogOutputStream(log))
        .allowIO(true)
        .allowHostClassLookup(s -> true)
        .option(JSContextOptions.ECMASCRIPT_VERSION_NAME, "2022");

    var ctx = ctxConfig.build();
    Value bindings = ctx.getBindings("js");
    bindings.putMember("mailService", mailService);
    bindings.putMember("fileService", fileService);
    bindings.putMember("feeService", feeService);
    bindings.putMember("clientService", clientService);
    bindings.putMember("dossierService", dossierService);
    bindings.putMember("timesheetService", timesheetService);
    bindings.putMember("reminderTaskService", reminderTaskService);
    bindings.putMember("invoiceService", invoiceService);
    bindings.putMember("documentService", documentService);
    bindings.putMember("personalInfoService", personalInfoService);
    bindings.putMember("mongoTemplate", mongoTemplate);
    bindings.putMember("notificationService", notificationService);
    bindings.putMember("curriculumService", curriculumService);
    bindings.putMember("generatePdf", CheckedFunction.toFunction(PdfToolBox::generatePDFFromHTML));
    bindings.putMember("logger", log);

    return ctx;

  }

}
