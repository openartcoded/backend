package tech.artcoded.websitev2.pages.cv.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScholarHistory implements Comparable<ScholarHistory>, Serializable {
  private Date from;
  private Date to;
  private boolean current;
  private String title;
  private String school;

  @Override
  public int compareTo(ScholarHistory o2) {
    if (this.isCurrent()) {
      return -1;
    } else if (o2.isCurrent()) {
      return 1;
    }
    return o2.getTo().compareTo(this.getTo());
  }
}
