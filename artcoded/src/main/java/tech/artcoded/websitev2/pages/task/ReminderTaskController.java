package tech.artcoded.websitev2.pages.task;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.artcoded.websitev2.action.ActionMetadata;
import tech.artcoded.websitev2.action.ActionResult;
import tech.artcoded.websitev2.action.ActionService;
import tech.artcoded.websitev2.rest.util.CronUtil;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reminder-task")
public class ReminderTaskController {
  private final ReminderTaskService service;
  private final ActionService actionService;

  public ReminderTaskController(ReminderTaskService service, ActionService actionService) {
    this.service = service;
    this.actionService = actionService;
  }

  @PostMapping(value = "/save")
  public ResponseEntity<Void> save(
      @RequestBody ReminderTask reminderTask) {
    service.save(reminderTask, true);
    return ResponseEntity.accepted().build();
  }

  @GetMapping("/find-all")
  public List<ReminderTask> get() {
    return service.findByOrderByNextDateAsc();
  }

  @GetMapping("find-next-ten-tasks")
  public List<ReminderTask> findNext10Tasks() {
    return service.findNext10Tasks();
  }

  @GetMapping("/action-results")
  public Page<ActionResult> getActionResults(@RequestParam("key") String actionKey, Pageable pageable) {
    return actionService.findActionResults(actionKey, pageable);
  }

  @GetMapping("/find-by-id")
  public ResponseEntity<ReminderTask> findById(@RequestParam("id") String id) {
    return service.findById(id).map(ResponseEntity::ok).orElseGet(ResponseEntity.noContent()::build);
  }

  @GetMapping("/allowed-actions")
  public List<ActionMetadata> allowedActions() {
    return actionService.getAllowedActions();
  }

  @DeleteMapping
  public ResponseEntity<Void> delete(@RequestParam("id") String id) {
    service.delete(id);
    return ResponseEntity.accepted().build();
  }

  @GetMapping("/validate-cron-expression")
  public Map.Entry<String, Boolean> validateCronExpression(@RequestParam("cronExpression") String cronExpression) {
    return Map.entry("valid", CronUtil.isValidCronExpression(cronExpression));
  }

}
