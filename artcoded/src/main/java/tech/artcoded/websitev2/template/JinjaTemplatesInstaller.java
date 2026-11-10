package tech.artcoded.websitev2.template;

import static tech.artcoded.websitev2.template.OaTemplateType.ACTION;
import static tech.artcoded.websitev2.template.OaTemplateType.CALENDAR;
import static tech.artcoded.websitev2.template.OaTemplateType.CV;
import static tech.artcoded.websitev2.template.OaTemplateType.DOSSIER_REPORT;
import static tech.artcoded.websitev2.template.OaTemplateType.EMAIL;
import static tech.artcoded.websitev2.template.OaTemplateType.INVOICE;
import static tech.artcoded.websitev2.template.OaTemplateType.PEPPOL_CREDIT_NOTE;
import static tech.artcoded.websitev2.template.OaTemplateType.PEPPOL_INVOICE;
import static tech.artcoded.websitev2.template.OaTemplateType.SMS;
import static tech.artcoded.websitev2.template.OaTemplateType.TIMESHEET;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.apache.commons.io.FilenameUtils;
import org.openapitools.client.api.UploadRoutesApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.rest.util.MockMultipartFile;

/**
 * Installs the jinja (v2) templates shipped with the application (resources templates/v2, converted from the FreeMarker
 * ones) as the default template of their type. Run once by a migration: it waits for the file service, which stores and
 * renders them.
 * <p>
 * Can run again after a failure: a template already installed (same type, name and version) is only made the default.
 */
@Component
@Slf4j
public class JinjaTemplatesInstaller {

    /** a jinja template shipped with the application */
    public record JinjaTemplate(OaTemplateType type, String path, String name, String description) {
    }

    public static final List<JinjaTemplate> TEMPLATES = List.of(
            new JinjaTemplate(INVOICE, "templates/v2/invoice-2027.html", "Invoice 2027",
                    "jinja version of template-invoice2027.ftl"),
            new JinjaTemplate(CV, "templates/v2/cv-2027.html", "CV 2027", "jinja version of template-cv2027.ftl"),
            new JinjaTemplate(TIMESHEET, "templates/v2/timesheet.html", "Timesheet",
                    "jinja version of timesheet-template-2021-v1.ftl"),
            new JinjaTemplate(PEPPOL_INVOICE, "templates/v2/peppol-invoice.xml", "Peppol invoice",
                    "jinja version of template-peppol-2025.xml (text escaped, real payment due date)"),
            new JinjaTemplate(PEPPOL_CREDIT_NOTE, "templates/v2/peppol-credit-note.xml", "Peppol credit note",
                    "jinja version of template-peppol-2025-creditnote.xml (text escaped)"),
            new JinjaTemplate(EMAIL, "templates/v2/email.html", "Email", "jinja version of email-template.ftl"),
            new JinjaTemplate(ACTION, "templates/v2/action.html", "Action report",
                    "jinja version of action-template.ftl"),
            new JinjaTemplate(SMS, "templates/v2/sms.txt", "Action SMS", "jinja version of action-sms-template.ftl"),
            new JinjaTemplate(CALENDAR, "templates/v2/calendar.ics", "Calendar invite",
                    "jinja version of make-ics.ftl"),
            new JinjaTemplate(DOSSIER_REPORT, "templates/v2/dossier-report.html", "Dossier report",
                    "monthly report of a dossier, compared with the previous years"));

    private final OaTemplateService templateService;
    private final OaTemplateRepository repository;
    private final UploadRoutesApi uploadRoutesApi;
    private final Duration fileServiceTimeout;
    private final Duration pollInterval;

    @Autowired
    public JinjaTemplatesInstaller(OaTemplateService templateService, OaTemplateRepository repository,
            UploadRoutesApi uploadRoutesApi,
            @Value("${application.templates.file-service-timeout:10m}") Duration fileServiceTimeout) {
        this(templateService, repository, uploadRoutesApi, fileServiceTimeout, Duration.ofSeconds(5));
    }

    JinjaTemplatesInstaller(OaTemplateService templateService, OaTemplateRepository repository,
            UploadRoutesApi uploadRoutesApi, Duration fileServiceTimeout, Duration pollInterval) {
        this.templateService = templateService;
        this.repository = repository;
        this.uploadRoutesApi = uploadRoutesApi;
        this.fileServiceTimeout = fileServiceTimeout;
        this.pollInterval = pollInterval;
    }

    /** Waits for the file service, then makes every shipped jinja template the default of its type. */
    public List<OaTemplate> install() {
        waitForFileService();
        return TEMPLATES.stream().map(this::install).toList();
    }

    /**
     * Waits for the file service, then makes the shipped jinja template of a type its default (a type added after the
     * first installation).
     */
    public OaTemplate install(OaTemplateType type) {
        var jinja = TEMPLATES.stream().filter(t -> t.type() == type).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no jinja template shipped for %s".formatted(type)));
        waitForFileService();
        return install(jinja);
    }

    /** Rollback of {@link #install(OaTemplateType)}: the latest v1 template of the type becomes the default again. */
    public void uninstall(OaTemplateType type) {
        repository.findByTypeAndLogicalDeleteIsFalseOrderByDateCreationDesc(type).stream()
                .filter(t -> t.getVersion() == OaTemplateVersion.V1).findFirst()
                .ifPresent(v1 -> templateService.setDefault(v1.getId()));
    }

    OaTemplate install(JinjaTemplate jinja) {
        var installed = findInstalled(jinja);
        if (installed.isPresent()) {
            log.info("{} jinja template already installed ({}), made the default", jinja.type(),
                    installed.get().getId());
            return templateService.setDefault(installed.get().getId()).orElse(installed.get());
        }
        var saved = templateService.save(null, jinja.name(), jinja.description(), jinja.type(), false, true,
                file(jinja));
        log.info("{} jinja template installed as the default ({})", jinja.type(), saved.getId());
        return saved;
    }

    /** Rollback: the latest v1 template of each type becomes the default again (the jinja ones are kept). */
    public void uninstall() {
        TEMPLATES.forEach(jinja -> uninstall(jinja.type()));
    }

    private Optional<OaTemplate> findInstalled(JinjaTemplate jinja) {
        return repository.findByTypeAndLogicalDeleteIsFalseOrderByDateCreationDesc(jinja.type()).stream()
                .filter(t -> t.getVersion() == OaTemplateVersion.V2 && jinja.name().equals(t.getName())).findFirst();
    }

    static MultipartFile file(JinjaTemplate jinja) {
        var fileName = FilenameUtils.getName(jinja.path());
        return MockMultipartFile.builder().name(fileName).originalFilename(fileName)
                .contentType(jinja.type().isXml() ? MediaType.APPLICATION_XML_VALUE : MediaType.TEXT_PLAIN_VALUE)
                .bytes(OaTemplateRenderer.readClasspath(jinja.path()).getBytes(StandardCharsets.UTF_8)).build();
    }

    void waitForFileService() {
        var deadline = Instant.now().plus(fileServiceTimeout);
        while (true) {
            try {
                if (uploadRoutesApi.pingWithHttpInfo().getStatusCode() == 200) {
                    return;
                }
            } catch (Exception e) {
                log.warn("file service not reachable yet: {}", e.getMessage());
            }
            if (Instant.now().isAfter(deadline)) {
                throw new IllegalStateException("the file service is not up after %s: jinja templates not installed"
                        .formatted(fileServiceTimeout));
            }
            try {
                Thread.sleep(pollInterval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting for the file service", e);
            }
        }
    }
}
