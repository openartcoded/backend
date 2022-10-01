package tech.artcoded.websitev2.action;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import tech.artcoded.websitev2.utils.helper.IdGenerators;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Document
public class ActionResult implements Serializable {
  private StatusType status;
  private String actionKey;

  @Id
  @Builder.Default
  private String id = IdGenerators.get();

  @Builder.Default
  private Date createdDate = new Date();

  @Builder.Default
  private List<String> messages = List.of();

  @Builder.Default
  private List<ActionParameter> parameters = List.of();

  private Date finishedDate;
  private Date startedDate;

}
