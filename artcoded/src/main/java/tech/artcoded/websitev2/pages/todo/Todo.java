package tech.artcoded.websitev2.pages.todo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import tech.artcoded.websitev2.api.helper.IdGenerators;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Document
public class Todo {
  @Id
  @Builder.Default
  private String id = IdGenerators.get();
  @Builder.Default
  private Date dateCreation = new Date();
  private Date updatedDate;
  private boolean done;
  private String title;
}
