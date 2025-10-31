package tech.artcoded.websitev2.pages.blog;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Document
public class Post {
  @Id
  private String id;
  private String title;
  @Builder.Default
  private String author = "system";
  private String description;
  private String coverId;
  @Builder.Default
  private Date creationDate = new Date();
  @Builder.Default
  private Date updatedDate = new Date();
  private transient String content;

  private boolean draft;
  private long countViews;

  @Builder.Default
  private Set<String> tags = Set.of();

}
