package tech.artcoded.websitev2.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class LogEvent {
  public enum LogEventType {
    INFO,
    WARN,
    ERROR
  }

  private String correlationId;
  private String message;

  @JsonFormat(pattern = "dd-MM-yyyy HH:mm:ss",
    timezone = "Europe/Brussels")
  @Builder.Default
  private Date creationDate = new Date();

  private LogEventType type;
}
