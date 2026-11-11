package tech.artcoded.websitev2.pages.timesheet.stats;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * The hours worked, from the timesheets: one row per year, month and client (minutes and working days). The frontend
 * builds the views from the rows (per year and client, cumulated per month...).
 *
 * @param workedDays
 *            per year, the calendar days with hours worked: a day counts once, however many hours. Only the years with a timesheet for each of the 12 months, and the current year
 */
public record TimesheetStats(List<Integer> years, List<String> clients, List<Row> rows,
        Map<Integer, Long> workedDays) implements Serializable {

    /**
     * @param month
     *            1 to 12
     * @param minutes
     *            minutes worked (working days only)
     * @param days
     *            working days with hours
     */
    public record Row(int year, int month, String client, long minutes, long days) implements Serializable {
    }
}
