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
public class Hobby implements Serializable {
  private static final long serialVersionUID = 1L;
  private String title;
  private String description;

}
