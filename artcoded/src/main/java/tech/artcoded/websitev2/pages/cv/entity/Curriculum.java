package tech.artcoded.websitev2.pages.cv.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import tech.artcoded.websitev2.api.helper.IdGenerators;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Document
public class Curriculum implements Serializable {
  @Id
  @Builder.Default
  private String id = IdGenerators.get();
  @Builder.Default
  private List<Experience> experiences = List.of();
  @Builder.Default
  private List<Skill> skills = List.of();
  @Builder.Default
  private List<PersonalProject> personalProjects = List.of();
  @Builder.Default
  private List<ScholarHistory> scholarHistories = List.of();
  @Builder.Default
  private List<Hobby> hobbies = List.of();
  private Person person;

  @Builder.Default
  private String introduction = "Change me";
  private Date updatedDate;
  private String freemarkerTemplateId;

}
