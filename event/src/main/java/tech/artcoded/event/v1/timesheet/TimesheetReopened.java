package tech.artcoded.event.v1.timesheet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class TimesheetReopened implements ITimesheetEvent {

  private String timesheetId;
  private String period;
  private String clientName;

}
