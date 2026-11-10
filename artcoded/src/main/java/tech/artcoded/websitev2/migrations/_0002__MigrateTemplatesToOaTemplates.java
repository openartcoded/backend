package tech.artcoded.websitev2.migrations;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.mongodb.client.model.Filters;

import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Rollback;
import io.flamingock.api.annotations.TargetSystem;
import lombok.extern.slf4j.Slf4j;
import tech.artcoded.websitev2.mongodb.MigrationTargetSystem;

/**
 * Moves the invoice and cv FreeMarker templates (one collection each) into the single oaTemplates collection, as v1
 * templates of type INVOICE / CV, with one default per type.
 * <p>
 * The ids are kept: invoices and the cv reference their template by id (freemarkerTemplateId), and the template files
 * are uploads correlated with that id. The legacy collections are left untouched (rollback, and the legacy change units
 * still write to them on a fresh database). Raw documents on purpose: a change must not depend on classes that evolve.
 */
@Slf4j
@TargetSystem(id = MigrationTargetSystem.ID)
@Change(id = "migrate-templates-to-oa-templates", author = "Nordine Bittich")
public class _0002__MigrateTemplatesToOaTemplates {
    static final String TARGET = "oaTemplates";
    static final String INVOICE_TEMPLATES = "invoiceFreemarkerTemplate";
    static final String CV_TEMPLATES = "curriculumFreemarkerTemplate";
    static final String CURRICULUM = "curriculum";

    @Apply
    public void apply(MongoTemplate mongoTemplate) {
        var target = mongoTemplate.getCollection(TARGET);
        Set<Object> alreadyMigrated = new HashSet<>();
        Set<String> typesWithDefault = new HashSet<>();
        target.find().forEach(d -> {
            alreadyMigrated.add(d.get("_id"));
            if (d.getBoolean("defaultTemplate", false) && !d.getBoolean("logicalDelete", false)) {
                typesWithDefault.add(d.getString("type"));
            }
        });

        // the cv template in use is the cv default
        Set<Object> cvTemplatesInUse = new HashSet<>();
        mongoTemplate.getCollection(CURRICULUM).find().forEach(cv -> {
            if (cv.get("freemarkerTemplateId") != null) {
                cvTemplatesInUse.add(cv.get("freemarkerTemplateId"));
            }
        });

        var toInsert = new ArrayList<Document>();
        mongoTemplate.getCollection(INVOICE_TEMPLATES).find().forEach(legacy -> {
            if (!alreadyMigrated.contains(legacy.get("_id"))) {
                var deleted = legacy.getBoolean("logicalDelete", false);
                toInsert.add(toOaTemplate(legacy, "INVOICE", !deleted && legacy.getBoolean("defaultTemplate", false),
                        deleted));
            }
        });
        mongoTemplate.getCollection(CV_TEMPLATES).find().forEach(legacy -> {
            if (!alreadyMigrated.contains(legacy.get("_id"))) {
                toInsert.add(toOaTemplate(legacy, "CV", cvTemplatesInUse.contains(legacy.get("_id")), false));
            }
        });
        ensureOneDefaultPerType(toInsert, typesWithDefault);
        if (!toInsert.isEmpty()) {
            target.insertMany(toInsert);
        }
        log.info("{} template(s) migrated to {}", toInsert.size(), TARGET);
    }

    /**
     * Every type gets exactly one default: the one flagged (the first one if several), otherwise the latest template
     * not deleted, which was the template used for new invoices before. Unless the type already has one.
     */
    static void ensureOneDefaultPerType(List<Document> templates, Set<String> typesWithDefault) {
        var byType = templates.stream().filter(d -> !d.getBoolean("logicalDelete", false))
                .collect(Collectors.groupingBy(d -> d.getString("type")));
        templates.forEach(d -> {
            if (typesWithDefault.contains(d.getString("type"))) {
                d.put("defaultTemplate", false);
            }
        });
        byType.forEach((type, ofType) -> {
            if (typesWithDefault.contains(type)) {
                return;
            }
            var defaults = ofType.stream().filter(d -> d.getBoolean("defaultTemplate", false)).toList();
            var chosen = defaults.isEmpty()
                    ? ofType.stream().max(Comparator.comparing((Document d) -> (Date) d.get("dateCreation")))
                            .orElseThrow()
                    : defaults.getFirst();
            ofType.forEach(d -> d.put("defaultTemplate", d == chosen));
        });
    }

    static Document toOaTemplate(Document legacy, String type, boolean defaultTemplate, boolean logicalDelete) {
        var name = legacy.getString("name");
        return new Document("_id", legacy.get("_id"))
                .append("dateCreation", legacy.get("dateCreation") != null ? legacy.get("dateCreation") : new Date())
                .append("updatedDate", legacy.get("updatedDate")).append("name", name != null ? name : "template")
                .append("description", null).append("type", type).append("version", "V1")
                .append("templateUploadId", legacy.get("templateUploadId")).append("fileServiceTemplateId", null)
                .append("defaultTemplate", defaultTemplate).append("logicalDelete", logicalDelete);
    }

    @Rollback
    public void rollback(MongoTemplate mongoTemplate) {
        // only what this change added: the v1 copies of the legacy templates
        var ids = new ArrayList<Object>();
        mongoTemplate.getCollection(INVOICE_TEMPLATES).find().forEach(d -> ids.add(d.get("_id")));
        mongoTemplate.getCollection(CV_TEMPLATES).find().forEach(d -> ids.add(d.get("_id")));
        if (!ids.isEmpty()) {
            mongoTemplate.getCollection(TARGET)
                    .deleteMany(Filters.and(Filters.in("_id", ids), Filters.eq("version", "V1")));
        }
    }
}
