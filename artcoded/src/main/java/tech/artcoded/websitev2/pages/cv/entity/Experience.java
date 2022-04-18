package tech.artcoded.websitev2.pages.cv.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Experience implements Comparable<Experience>, Serializable {
  private Date from;
  private Date to;
  private boolean current;
  private String title;
  @Builder.Default
  private List<String> description = List.of();
  private String company;

  @Override
  public int compareTo(Experience o2) {
    if (this.isCurrent()) {
      return -1;
    } else if (o2.isCurrent()) {
      return 1;
    }
    return o2.getTo().compareTo(this.getTo());
  }
}
