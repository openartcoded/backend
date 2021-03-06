package tech.artcoded.websitev2.pages.settings.menu;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Document
public class MenuLink {
  @Id
  private String id;
  private int order;
  private Date updatedDate;
  private String title;
  private String description;
  private RouterLinkOption routerLinkActiveOptions;
  private String[] icon;
  private String[] routerLink;
  @Builder.Default
  private boolean show = true;
}
