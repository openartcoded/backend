package tech.artcoded.websitev2.pages.cv.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonalProject implements Serializable {
  private String name;
  private String url;
  private String description;
}
