package tech.artcoded.websitev2.pages.fee;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class FeeSearchCriteria {
    private String id;
    private Date dateBefore;
    private Date dateAfter;
    private String subject;
    private String body;
    private boolean archived;
    private String tag;
    private boolean bookmarked;

}
