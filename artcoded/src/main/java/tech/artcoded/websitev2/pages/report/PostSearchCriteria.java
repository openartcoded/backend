package tech.artcoded.websitev2.pages.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class PostSearchCriteria {
    private String id;
    private Date dateBefore;
    private Date dateAfter;
    private String title;
    private String content;
    private String tag;

}
