package tech.artcoded.websitev2.template;

import java.util.Date;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.artcoded.websitev2.utils.helper.IdGenerators;

/**
 * Every template of the application (invoice, cv, timesheet, peppol), whatever renders it. Replaces the
 * invoiceFreemarkerTemplate and curriculumFreemarkerTemplate collections, migrated with the same ids so invoices and
 * the cv keep pointing to their template (freemarkerTemplateId).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Document(collection = OaTemplate.COLLECTION)
public class OaTemplate {
    public static final String COLLECTION = "oaTemplates";

    @Id
    @Builder.Default
    private String id = IdGenerators.get();
    @Builder.Default
    private Date dateCreation = new Date();
    private Date updatedDate;
    private String name;
    private String description;
    private OaTemplateType type;
    @Builder.Default
    private OaTemplateVersion version = OaTemplateVersion.V2;
    /** v1 only: the uploaded FreeMarker file (correlated with the template id). */
    private String templateUploadId;
    /** v2 only: the id of the template in the file service. */
    private String fileServiceTemplateId;
    /** One default per type, whatever its version: the template rendered for the type. */
    private boolean defaultTemplate;
    /** Deleted while still used (e.g. by an invoice): hidden, but still renders what uses it. */
    private boolean logicalDelete;
}
