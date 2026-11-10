package tech.artcoded.websitev2.pages.invoice;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import tech.artcoded.websitev2.utils.helper.IdGenerators;

import java.util.Date;

/**
 * @deprecated legacy collection, migrated to {@link tech.artcoded.websitev2.template.OaTemplate} (oaTemplates). Only
 *             kept for the legacy change units that still write to it on a fresh database.
 */
@Deprecated
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Document
public class InvoiceFreemarkerTemplate {
    @Id
    @Builder.Default
    private String id = IdGenerators.get();
    @Builder.Default
    private Date dateCreation = new Date();
    private Date updatedDate;
    private String name;
    private String templateUploadId;
    private boolean logicalDelete;
    /**
     * When true, new invoices (from the timesheet, from scratch or from an existing invoice) use this template. At most
     * one template is the default; without one, the latest template is used.
     */
    private boolean defaultTemplate;
}
