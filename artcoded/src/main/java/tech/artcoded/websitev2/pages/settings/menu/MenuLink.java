package tech.artcoded.websitev2.pages.settings.menu;

import java.util.Date;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import tech.artcoded.websitev2.utils.helper.IdGenerators;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Document
public class MenuLink {
  @Id
  @Builder.Default
  private String id = IdGenerators.get();
  private int order;
  private Date updatedDate;
  private String title;
  private String description;
  private RouterLinkOption routerLinkActiveOptions;
  private String[] icon;
  private String[] routerLink;
  @Builder.Default
  private boolean show = true;
  @Builder.Default
  public Long numberOfTimesClicked = 0L;

  private List<String> roles;
}
