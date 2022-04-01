package tech.artcoded.websitev2.pages.blog;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Document
public class PostSearchCriteria {
  private String id;
  private Date datebefore;
  private Date dateAfter;
  private String title;
  private String content;
  private String tag;

}
