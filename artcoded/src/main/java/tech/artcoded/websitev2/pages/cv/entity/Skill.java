package tech.artcoded.websitev2.pages.cv.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Skill implements Serializable {
  private int priority;
  private String name;
  private boolean softSkill;
  private boolean hardSkill;
  @Builder.Default
  private List<String> tags = List.of();
}
