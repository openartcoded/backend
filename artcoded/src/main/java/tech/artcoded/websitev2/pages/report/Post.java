package tech.artcoded.websitev2.pages.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.artcoded.websitev2.pages.postit.PostIt;

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
  private String content;

  @Builder.Default
  private Set<String> attachmentIds = Set.of();

  @Builder.Default
  private Set<String> processedAttachmentIds = Set.of();

  @Builder.Default
  private Set<PostIt> todos = Set.of();

  @Builder.Default
  private Set<PostIt> inProgress = Set.of();

  @Builder.Default
  private Set<PostIt> done = Set.of();

  private boolean draft;

  @Builder.Default
  private Set<String> tags = Set.of();

  public enum PostItType {
    TODOS, IN_PROGRESS, DONE
  }

}
