package tech.artcoded.websitev2.template;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.openapitools.client.api.TemplateRoutesApi;
import org.openapitools.client.model.TemplateV2;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.pages.cv.entity.Curriculum;
import tech.artcoded.websitev2.pages.invoice.InvoiceGeneration;
import tech.artcoded.websitev2.upload.IFileUploadService;
import tech.artcoded.websitev2.upload.ILinkable;
import tech.artcoded.websitev2.utils.helper.IdGenerators;

/**
 * The single place to manage templates. Any number of v1 (FreeMarker, rendered by the backend) and v2 (stored and
 * rendered by the file service) templates per type; one of them, whatever its version, is the default of the type: the
 * one rendered.
 */
@Service
@Slf4j
public class OaTemplateService implements ILinkable {
    /** field holding the template id in the documents that chose a template (invoices, cv) */
    static final String TEMPLATE_REF_FIELD = "freemarkerTemplateId";

    private final OaTemplateRepository repository;
    private final IFileUploadService fileUploadService;
    private final TemplateRoutesApi templateRoutesApi;
    private final MongoTemplate mongoTemplate;

    public OaTemplateService(OaTemplateRepository repository, IFileUploadService fileUploadService,
            TemplateRoutesApi templateRoutesApi, MongoTemplate mongoTemplate) {
        this.repository = repository;
        this.fileUploadService = fileUploadService;
        this.templateRoutesApi = templateRoutesApi;
        this.mongoTemplate = mongoTemplate;
    }

    /** Templates not deleted, latest first, optionally of one type. */
    public List<OaTemplate> list(Optional<OaTemplateType> type) {
        return type.map(repository::findByTypeAndLogicalDeleteIsFalseOrderByDateCreationDesc)
                .orElseGet(repository::findByLogicalDeleteIsFalseOrderByDateCreationDesc);
    }

    /** Any template, even a deleted one: what was rendered with it must still render. */
    public Optional<OaTemplate> findById(String id) {
        return Optional.ofNullable(id).filter(StringUtils::isNotBlank).flatMap(repository::findById);
    }

    /** The default template of a type, the one rendered. */
    public Optional<OaTemplate> getDefault(OaTemplateType type) {
        return repository.findFirstByTypeAndDefaultTemplateIsTrueAndLogicalDeleteIsFalse(type);
    }

    /**
     * Adds a template (id blank) or overwrites one (same id: what uses it now renders with the new content).
     *
     * @param v1
     *            true: a FreeMarker template rendered by the backend, false: a jinja template rendered by the file
     *            service
     * @param makeDefault
     *            the template becomes the default of its type. The first template of a type always does.
     * @param file
     *            can be omitted on an overwrite keeping the version, to rename the template
     */
    @SneakyThrows
    @CacheEvict(cacheNames = "cvPdf", allEntries = true)
    public OaTemplate save(String id, String name, String description, OaTemplateType type, boolean v1,
            boolean makeDefault, MultipartFile file) {
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("a template needs a name");
        }
        if (type == null) {
            throw new IllegalArgumentException("a template needs a type");
        }
        var version = v1 ? OaTemplateVersion.V1 : OaTemplateVersion.V2;
        Optional<OaTemplate> existing = Optional.empty();
        if (StringUtils.isNotBlank(id)) {
            existing = Optional.of(repository.findById(id).filter(Predicate.not(OaTemplate::isLogicalDelete))
                    .orElseThrow(() -> new IllegalArgumentException("template %s not found".formatted(id))));
            if (existing.get().getType() != type) {
                throw new IllegalArgumentException(
                        "the type of a template cannot change (%s)".formatted(existing.get().getType()));
            }
        }

        OaTemplate saved;
        if (file == null || file.isEmpty()) {
            var current = existing.filter(e -> e.getVersion() == version)
                    .orElseThrow(() -> new IllegalArgumentException("a template file is required"));
            saved = repository
                    .save(current.toBuilder().name(name).description(description).updatedDate(new Date()).build());
        } else {
            var template = existing.map(e -> e.toBuilder().updatedDate(new Date()))
                    .orElseGet(() -> OaTemplate.builder().type(type)).name(name).description(description)
                    .version(version).build();

            if (version == OaTemplateVersion.V1) {
                String uploadId = fileUploadService.upload(file, template.getId(), false);
                existing.ifPresent(previous -> removeContent(previous, uploadId));
                template = template.toBuilder().templateUploadId(uploadId).fileServiceTemplateId(null).build();
            } else {
                var context = type.fileServiceContext().orElseThrow(() -> new IllegalArgumentException(
                        "the file service cannot render %s templates: upload it as a v1 template".formatted(type)));
                // overwriting a v2 template keeps its id in the file service
                var fileServiceId = existing.filter(e -> e.getVersion() == OaTemplateVersion.V2)
                        .map(OaTemplate::getFileServiceTemplateId).orElse(null);
                TemplateV2 stored = upsertInFileService(fileServiceId, name, description, type, context, file);
                existing.filter(e -> e.getVersion() == OaTemplateVersion.V1)
                        .ifPresent(previous -> removeContent(previous, null));
                template = template.toBuilder().fileServiceTemplateId(stored.getId()).templateUploadId(null).build();
            }
            saved = repository.save(template);
        }

        if (makeDefault || getDefault(type).isEmpty()) {
            return markDefault(saved);
        }
        return saved;
    }

    @SneakyThrows
    private TemplateV2 upsertInFileService(String fileServiceId, String name, String description,
            OaTemplateType type, org.openapitools.client.model.Context context, MultipartFile file) {
        // the file service checks (and for text, derives the content type from) the extension: the one of the type
        var baseName = FilenameUtils.getBaseName(
                fileUploadService.normalizeFilename(StringUtils.defaultIfBlank(file.getOriginalFilename(), "template")));
        var tmpDir = new File(fileUploadService.getTempFolder(), IdGenerators.get());
        try {
            var tmpFile = new File(tmpDir, StringUtils.defaultIfBlank(baseName, "template") + type.extension());
            FileUtils.copyInputStreamToFile(file.getInputStream(), tmpFile);
            return templateRoutesApi.upsert(name, type.fileServiceTemplateType(), context, List.of(tmpFile),
                    fileServiceId, description);
        } finally {
            FileUtils.deleteQuietly(tmpDir);
        }
    }

    /**
     * Removes what holds the content of a template (its upload or its file service template).
     *
     * @param keepUploadId
     *            v1 upload that just replaced the previous one, never removed
     */
    private void removeContent(OaTemplate template, String keepUploadId) {
        try {
            switch (template.getVersion()) {
            case V1 -> {
                if (StringUtils.isNotBlank(template.getTemplateUploadId())
                        && !template.getTemplateUploadId().equals(keepUploadId)) {
                    fileUploadService.delete(template.getTemplateUploadId());
                }
            }
            case V2 -> {
                if (StringUtils.isNotBlank(template.getFileServiceTemplateId())) {
                    templateRoutesApi.deleteTemplById(template.getFileServiceTemplateId());
                }
            }
            }
        } catch (Exception e) {
            log.error("could not remove the content of template {}", template.getId(), e);
        }
    }

    /**
     * Makes a template the default of its type, the previous default stops being one. Empty when the template doesn't
     * exist or is deleted.
     */
    @CacheEvict(cacheNames = "cvPdf", allEntries = true)
    public Optional<OaTemplate> setDefault(String id) {
        return findById(id).filter(Predicate.not(OaTemplate::isLogicalDelete)).map(this::markDefault);
    }

    private OaTemplate markDefault(OaTemplate template) {
        repository.findByTypeAndDefaultTemplateIsTrue(template.getType()).stream()
                .filter(other -> !other.getId().equals(template.getId())).forEach(other -> repository
                        .save(other.toBuilder().updatedDate(new Date()).defaultTemplate(false).build()));
        return template.isDefaultTemplate() ? template
                : repository.save(template.toBuilder().updatedDate(new Date()).defaultTemplate(true).build());
    }

    /**
     * The default template cannot be deleted (make another one the default first). A template still used by an invoice
     * or the cv is only flagged as deleted, otherwise it's removed.
     */
    public void delete(String id) {
        findById(id).ifPresent(template -> {
            if (template.isDefaultTemplate() && !template.isLogicalDelete()) {
                throw new IllegalArgumentException(
                        "%s is the default %s template: make another template the default first"
                                .formatted(template.getName(), template.getType()));
            }
            if (isUsed(id)) {
                repository.save(template.toBuilder().updatedDate(new Date()).logicalDelete(true).build());
            } else {
                if (template.getVersion() == OaTemplateVersion.V1) {
                    fileUploadService.deleteByCorrelationId(template.getId());
                } else {
                    removeContent(template, null);
                }
                repository.deleteById(template.getId());
            }
        });
    }

    boolean isUsed(String id) {
        var used = query(where(TEMPLATE_REF_FIELD).is(id));
        return mongoTemplate.exists(used, InvoiceGeneration.class) || mongoTemplate.exists(used, Curriculum.class);
    }

    /** The FreeMarker source of a v1 template. */
    public String v1Source(OaTemplate template) {
        if (template.getVersion() != OaTemplateVersion.V1) {
            throw new IllegalArgumentException("template %s is not a v1 template".formatted(template.getId()));
        }
        return fileUploadService.findOneById(template.getTemplateUploadId())
                .map(fileUploadService::uploadToInputStream).map(is -> {
                    try (is) {
                        return IOUtils.toString(is, StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }).orElseThrow(() -> new RuntimeException(
                        "the file of template %s (%s) is missing".formatted(template.getName(), template.getId())));
    }

    /** The template file as uploaded, whatever its version (to download it before overwriting it). */
    @SneakyThrows
    public byte[] source(OaTemplate template) {
        return switch (template.getVersion()) {
        case V1 -> v1Source(template).getBytes(StandardCharsets.UTF_8);
        case V2 -> fileUploadService
                .uploadToByteArray(templateRoutesApi.findOne(template.getFileServiceTemplateId()).getFileId());
        };
    }

    /** The variables a v2 template reads (e.g. invoice.total). Empty for a v1 template. */
    @SneakyThrows
    public List<String> variables(OaTemplate template) {
        return template.getVersion() == OaTemplateVersion.V2
                ? templateRoutesApi.variables(template.getFileServiceTemplateId())
                : List.of();
    }

    @Override
    public String getCorrelationLabel(String correlationId) {
        return repository.findById(correlationId).map(this::toLabel).orElse(null);
    }

    @Override
    public Map<String, String> getCorrelationLabels(Collection<String> correlationIds) {
        return repository.findAllById(correlationIds).stream()
                .collect(Collectors.toMap(OaTemplate::getId, this::toLabel, (a, b) -> a));
    }

    private String toLabel(OaTemplate t) {
        return "%s Template %s".formatted(t.getType(), t.getName());
    }

    @Override
    public void updateOldId(String correlationId, String oldId, String newId) {
        repository.findById(correlationId).filter(t -> oldId.equals(t.getTemplateUploadId())).ifPresent(
                t -> repository.save(t.toBuilder().templateUploadId(newId).updatedDate(new Date()).build()));
    }
}
