package tech.artcoded.websitev2.template;

import java.util.Optional;

import org.openapitools.client.model.Context;
import org.openapitools.client.model.TemplateType;

/**
 * What a template renders. Every type has one default template (v1 or v2), and a template bundled with the application
 * (FreeMarker, used when the type has no default yet).
 */
public enum OaTemplateType {
    INVOICE(Output.PDF, Context.INVOICE, "invoice/template-end-2022.ftl", ".html"),
    CV(Output.PDF, Context.CV, "cv/cv-template-2021.ftl", ".html"),
    TIMESHEET(Output.PDF, Context.TIMESHEET, "timesheet/timesheet-template-2021-v1.ftl", ".html"),
    PEPPOL_INVOICE(Output.XML, Context.PEPPOL, "invoice/template-peppol-2025.xml", ".xml"),
    PEPPOL_CREDIT_NOTE(Output.XML, Context.PEPPOL, "invoice/template-peppol-2025-creditnote.xml", ".xml"),
    /** the html around every email sent (subject, htmlBody, attachments) */
    EMAIL(Output.TEXT, Context.EMAIL, "templates/email-template.ftl", ".html"),
    /** the html email reporting the result of an action (actionResult) */
    ACTION(Output.TEXT, Context.ACTION, "templates/action-template.ftl", ".html"),
    /** the sms reporting the result of an action (actionResult, actionKey) */
    SMS(Output.TEXT, Context.SMS, "templates/action-sms-template.ftl", ".txt"),
    /** the .ics invite attached to a reminder */
    CALENDAR(Output.TEXT, Context.CALENDAR, "templates/make-ics.ftl", ".ics"),
    /**
     * the monthly report of a dossier (report: see DossierReport). The file service has no report context: a template's
     * context only has to match the one of its rendering requests, ACTION is the closest one.
     */
    DOSSIER_REPORT(Output.PDF, Context.ACTION, "templates/dossier-report.ftl", ".html");

    /** what a rendering gives */
    public enum Output {
        /** html printed to a pdf */
        PDF, XML,
        /** the rendered text itself (html of an email, sms, ics) */
        TEXT
    }

    private final Output output;
    private final Context fileServiceContext;
    private final String bundledTemplate;
    private final String extension;

    OaTemplateType(Output output, Context fileServiceContext, String bundledTemplate, String extension) {
        this.output = output;
        this.fileServiceContext = fileServiceContext;
        this.bundledTemplate = bundledTemplate;
        this.extension = extension;
    }

    public Output output() {
        return output;
    }

    public boolean isXml() {
        return output == Output.XML;
    }

    /** The context in the file service, empty when the file service cannot render this type. */
    public Optional<Context> fileServiceContext() {
        return Optional.ofNullable(fileServiceContext);
    }

    public TemplateType fileServiceTemplateType() {
        return switch (output) {
            case PDF -> TemplateType.HTML;
            case XML -> TemplateType.XML;
            case TEXT -> TemplateType.TEXT;
        };
    }

    /** Classpath FreeMarker template shipped with the application. */
    public String bundledTemplate() {
        return bundledTemplate;
    }

    /**
     * Extension of the file of a v2 template: the file service checks it, and for a text template derives the content
     * type of the rendering from it.
     */
    public String extension() {
        return extension;
    }
}
