package tech.artcoded.websitev2.pages.task;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import tech.artcoded.websitev2.notification.NotificationService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;
import static tech.artcoded.websitev2.pages.task.ReminderTaskService.REMINDER_TASK_ADD_OR_UPDATE;

@ExtendWith(SpringExtension.class)
class ReminderTaskServiceTest {
  @Mock
  private ReminderTaskRepository repository;
  @Mock
  private NotificationService notificationService;
  @InjectMocks
  private ReminderTaskService reminderTaskService;

  @BeforeEach
  public void setup() {
    Mockito.reset(repository, notificationService);
  }

  @Test
  void save() {
    Mockito.when(repository.save(Mockito.any(ReminderTask.class)))
        .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0, ReminderTask.class));

    ReminderTask task = ReminderTask.builder().title("A Title").description("A Description")
        .cronExpression("0 0 0 * * *").build();

    reminderTaskService.save(task, true);

    ArgumentCaptor<ReminderTask> argument = ArgumentCaptor.forClass(ReminderTask.class);
    verify(repository).save(argument.capture());
    ReminderTask saved = argument.getValue();

    LocalTime midnight = LocalTime.MIDNIGHT;
    LocalDate today = LocalDate.now(ZoneId.systemDefault());
    LocalDateTime tomorrowMidnight = LocalDateTime.of(today, midnight).plusDays(1);

    assertEquals(Date.from(tomorrowMidnight.atZone(ZoneId.systemDefault()).toInstant()), saved.getNextDate());
    verify(notificationService, times(1)).sendEvent("task 'A Title' saved or updated", REMINDER_TASK_ADD_OR_UPDATE,
        saved.getId());
    verifyNoMoreInteractions(notificationService);
  }

  @Test
  void saveNextYear() {
    Mockito.when(repository.save(Mockito.any(ReminderTask.class)))
        .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0, ReminderTask.class));
    LocalDateTime now = LocalDateTime.now();
    int hour = now.getHour();
    int minute = now.getMinute();
    int second = now.getSecond();
    int dayOfMonth = now.getDayOfMonth();
    int month = now.getMonth().getValue();
    ReminderTask task = ReminderTask.builder().title("A Title").description("A Description")
        .cronExpression("%s %s %s %s %s ?".formatted(second, minute, hour, dayOfMonth, month))
        .dateCreation(Date.from(now.atZone(ZoneId.systemDefault()).toInstant())).build();

    reminderTaskService.save(task, true);

    ArgumentCaptor<ReminderTask> argument = ArgumentCaptor.forClass(ReminderTask.class);
    verify(repository).save(argument.capture());
    ReminderTask saved = argument.getValue();

    var formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    assertEquals(formatter.format(now.plusYears(1)),
        formatter.format(LocalDateTime.ofInstant(saved.getNextDate().toInstant(), ZoneId.systemDefault())));
    verify(notificationService, times(1)).sendEvent("task 'A Title' saved or updated", REMINDER_TASK_ADD_OR_UPDATE,
        saved.getId());
    verifyNoMoreInteractions(notificationService);
  }

  @Test
  void saveDisabled() {
    Mockito.when(repository.save(Mockito.any(ReminderTask.class)))
        .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0, ReminderTask.class));
    Date currentDate = new Date();
    when(repository.findById(anyString()))
        .thenReturn(
            Optional.of(ReminderTask.builder().specificDate(currentDate).lastExecutionDate(currentDate).build()));
    ReminderTask task = ReminderTask.builder().title("A Title").description("A Description")
        .specificDate(currentDate).lastExecutionDate(new Date()).build();

    reminderTaskService.save(task, true);

    ArgumentCaptor<ReminderTask> argument = ArgumentCaptor.forClass(ReminderTask.class);
    verify(repository).save(argument.capture());
    ReminderTask saved = argument.getValue();

    assertTrue(saved.isDisabled());
    verify(notificationService, times(1)).sendEvent("task 'A Title' saved or updated", REMINDER_TASK_ADD_OR_UPDATE,
        saved.getId());
    verifyNoMoreInteractions(notificationService);
  }
}
