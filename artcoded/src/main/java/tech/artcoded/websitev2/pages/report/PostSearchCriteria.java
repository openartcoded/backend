package tech.artcoded.websitev2.pages.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.artcoded.websitev2.pages.report.Post.PostStatus;
import tech.artcoded.websitev2.pages.report.Post.Priority;

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
    private PostStatus status;
    private Priority priority;
    private Boolean bookmarked;
    private String tag;

}
