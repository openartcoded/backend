package tech.artcoded.websitev2.pages.task;


import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tech.artcoded.websitev2.action.ActionMetadata;
import tech.artcoded.websitev2.action.ActionResult;
import tech.artcoded.websitev2.action.ActionService;
import tech.artcoded.websitev2.rest.annotation.SwaggerHeaderAuthentication;

import java.util.List;


@RestController
@RequestMapping("/api/reminder-task")
@Slf4j
public class ReminderTaskController {
  private final ReminderTaskService service;
  private final ActionService actionService;

  public ReminderTaskController(ReminderTaskService service, ActionService actionService) {
    this.service = service;
    this.actionService = actionService;
  }

  @PostMapping(value = "/save")
  @SwaggerHeaderAuthentication
  public ResponseEntity<Void> save(
          @RequestBody ReminderTask reminderTask) {
    service.save(reminderTask, true);
    return ResponseEntity.accepted().build();
  }

  @GetMapping("/find-all")
  @SwaggerHeaderAuthentication
  public List<ReminderTask> get() {
    return service.findByOrderByNextDateDesc();
  }

  @GetMapping("/action-results")
  @SwaggerHeaderAuthentication
  public Page<ActionResult> getActionResults(@RequestParam("key") String actionKey, Pageable pageable) {
    return actionService.findActionResults(actionKey, pageable);
  }

  @GetMapping("/find-by-id")
  @SwaggerHeaderAuthentication
  public ResponseEntity<ReminderTask> findById(@RequestParam("id") String id) {
    return service.findById(id).map(ResponseEntity::ok).orElseGet(ResponseEntity.noContent()::build);
  }

  @GetMapping("/allowed-actions")
  @SwaggerHeaderAuthentication
  public List<ActionMetadata> allowedActions() {
    return actionService.getAllowedActions();
  }

  @DeleteMapping
  @SwaggerHeaderAuthentication
  public ResponseEntity<Void> delete(@RequestParam("id") String id) {
    service.delete(id);
    return ResponseEntity.accepted().build();
  }

}