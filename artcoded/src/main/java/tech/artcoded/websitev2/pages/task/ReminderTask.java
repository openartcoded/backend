package tech.artcoded.websitev2.pages.task;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import tech.artcoded.websitev2.action.ActionParameter;
import tech.artcoded.websitev2.utils.helper.IdGenerators;

import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Document
public class ReminderTask {
  @Id
  @Builder.Default
  private String id = IdGenerators.get();
  @Builder.Default
  private Date dateCreation = new Date();
  private Date updatedDate;
  private Date lastExecutionDate;
  private Date nextDate;
  private Date specificDate;
  private String cronExpression;
  private String title;
  private String description;
  private boolean disabled;
  private boolean sendMail;
  @Builder.Default
  private boolean sendSms = false;
  @Builder.Default
  private boolean inAppNotification = true;
  private String actionKey;
  private String customActionName;
  private boolean persistResult;
  @Builder.Default
  private List<ActionParameter> actionParameters = List.of();
}
