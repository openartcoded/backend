package tech.artcoded.websitev2.pages.timesheet.stats;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/timesheet/stats")
public class TimesheetStatsController {
    private final TimesheetStatsService service;

    public TimesheetStatsController(TimesheetStatsService service) {
        this.service = service;
    }

    /** hours worked per year, month and client (cached) */
    @GetMapping
    public TimesheetStats stats() {
        return service.stats();
    }
}
