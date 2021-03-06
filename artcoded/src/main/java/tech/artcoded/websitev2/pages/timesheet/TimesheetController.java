package tech.artcoded.websitev2.pages.timesheet;


import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/timesheet")
@Slf4j
public class TimesheetController {
  private final TimesheetService service;

  public TimesheetController(TimesheetService service) {
    this.service = service;
  }

  @GetMapping
  public Map<Integer, List<Timesheet>> findAllGroupedByYear() {
    return this.service.findAllGroupedByYear();
  }

  @GetMapping("by-id")
  public ResponseEntity<Timesheet> findAllGroupedByYear(@RequestParam("id") String id) {
    return this.service.findById(id).map(ResponseEntity::ok).orElseGet(ResponseEntity.noContent()::build);
  }

  @GetMapping("/count")
  public Map.Entry<String, Long> count() {
    return Map.entry("message", this.service.count());
  }

  @PostMapping
  public Timesheet saveOrUpdateTimesheet(@RequestBody Timesheet timesheet) {
    return service.saveOrUpdateTimesheet(timesheet);
  }

  @PostMapping("/save-period")
  public TimesheetPeriod saveOrUpdateTimesheetPeriod(@RequestParam("id") String id,
                                                     @RequestBody TimesheetPeriod timesheetPeriod) {
    return service.saveOrUpdateTimesheetPeriod(id, timesheetPeriod);

  }

  @PostMapping("/close")
  public void closeTimesheet(@RequestParam("id") String id) {
    service.closeTimesheet(id);
  }

  @PostMapping("/reopen")
  public void reopenTimesheet(@RequestParam("id") String id) {
    service.reopenTimesheet(id);
  }

  @GetMapping("/settings")
  public TimesheetSettings getSettings() {
    return service.getSettings();

  }

  @PostMapping("/settings")
  public TimesheetSettings updateSettings(@RequestBody TimesheetSettings settings) {
    return service.updateSettings(settings);

  }

  @DeleteMapping()
  public void deleteTimesheet(@RequestParam("id") String id) {
    service.deleteById(id);

  }
}
