package tech.artcoded.websitev2.pages.timesheet;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/timesheet")
public class TimesheetController {
  private final TimesheetService service;

  public TimesheetController(TimesheetService service) {
    this.service = service;
  }

  @GetMapping
  public Map<Integer, Map<String, List<Timesheet>>> findAllGroupedByYearAndClientName() {
    return this.service.findAllGroupedByYearAndClientName();
  }

  @GetMapping("by-id")
  public ResponseEntity<Timesheet> findById(@RequestParam("id") String id) {
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

  @PostMapping("/generate-invoice")
  public Timesheet generateInvoice(@RequestParam("id") String id) {
    return this.service.generateInvoiceFromTimesheet(id);
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

  @PostMapping("/settings")
  public Timesheet updateSettings(@RequestBody TimesheetSettingsForm settings) {
    return service.updateSettings(settings);

  }

  @DeleteMapping()
  public void deleteTimesheet(@RequestParam("id") String id) {
    service.deleteById(id);

  }
}
