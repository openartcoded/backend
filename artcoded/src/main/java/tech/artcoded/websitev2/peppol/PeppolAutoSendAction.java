
package tech.artcoded.websitev2.peppol;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.action.*;
import tech.artcoded.websitev2.pages.invoice.InvoiceGeneration;
import tech.artcoded.websitev2.pages.invoice.InvoiceGenerationRepository;
import tech.artcoded.websitev2.upload.IFileUploadService;
import tech.artcoded.websitev2.utils.helper.DateHelper;
import tech.artcoded.websitev2.utils.service.MailService;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
@Profile({ "dev", "prod" })
@Slf4j
public class PeppolAutoSendAction implements Action {
    public static final String ACTION_KEY = "PEPPOL_AUTO_SEND_ACTION";

    @Value("${application.admin.email}")
    private String adminEmail;

    private final InvoiceGenerationRepository invoiceRepository;
    private final MailService mailService;
    private final PeppolService peppolService;
    private final IFileUploadService fileUploadService;

    public PeppolAutoSendAction(InvoiceGenerationRepository invoiceRepository, PeppolService peppolService,
            IFileUploadService fileUploadService, MailService mailService) {
        this.invoiceRepository = invoiceRepository;
        this.mailService = mailService;
        this.fileUploadService = fileUploadService;
        this.peppolService = peppolService;
    }

    List<InvoiceGeneration> getInvoices() {
        return invoiceRepository.findByLogicalDeleteIsFalseAndArchivedIsTrueAndPeppolStatusIs(PeppolStatus.NOT_SENT)
                .stream()
                .filter(i -> DateHelper.toLocalDate(i.getDateOfInvoice()).isBefore(DateHelper.toLocalDate(new Date())))
                .toList();
    }

    @Override
    public boolean shouldNotRun(List<ActionParameter> parameters) {
        return getInvoices().isEmpty();
    }

    @Override
    public ActionResult run(List<ActionParameter> parameters) {
        var resultBuilder = this.actionResultBuilder(parameters);
        List<String> messages = new ArrayList<>();
        try {

            messages.add("getting unsent invoices...");
            var invoices = getInvoices();
            messages.add("found " + invoices.size() + " invoices");
            if (!invoices.isEmpty()) {
                for (var invoice : invoices) {
                    try {
                        messages.add("sending invoice with id %s to peppol".formatted(invoice.getId()));
                        peppolService.addInvoice(invoice);
                    } catch (Exception e) {
                        messages.add("could not send invoice with id %s to peppol, skip".formatted(invoice.getId()));
                        mailService.sendMail(List.of(adminEmail), "PEPPOL_AUTO_SEND_ACTION: invoice failed ", """
                                 Could not process invoice with id %s, ref %s and number %s.
                                """.formatted(invoice.getId(), invoice.getReference(), invoice.getNewInvoiceNumber()),
                                false, () -> fileUploadService.findByCorrelationId(false, invoice.getId()).stream()
                                        .map(fileUploadService::getFile).toList());
                    }
                }

            } else {
                messages.add("no op");
            }
            return resultBuilder.finishedDate(new Date()).status(StatusType.UNKNOWN).messages(messages).build();
        } catch (Exception e) {
            log.error("error while executing action", e);
            messages.add("error, see logs: %s".formatted(e.getMessage()));
            return resultBuilder.messages(messages).finishedDate(new Date()).status(StatusType.FAILURE).build();
        }
    }

    @Override
    public ActionMetadata getMetadata() {
        return defaultMetadata();
    }

    @Override
    public String getKey() {
        return ACTION_KEY;
    }

    public static ActionMetadata defaultMetadata() {
        return ActionMetadata.builder().key(ACTION_KEY).title("Peppol Autosend Action")
                .description("An action to automatically send unsent archived peppol invoices.")
                .allowedParameters(List.of()).defaultCronValue("0 30 4 * * ?").build();
    }
}
