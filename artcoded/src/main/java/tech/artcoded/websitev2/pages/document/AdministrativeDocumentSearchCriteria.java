package tech.artcoded.websitev2.pages.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class AdministrativeDocumentSearchCriteria {
    private String id;
    private List<String> tags;
    private String title;
    private String description;
    private Date dateBefore;
    private Date dateAfter;
    private boolean bookmarked;
}
