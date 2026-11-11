package tech.artcoded.websitev2.pages.timesheet.stats;

import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import tech.artcoded.websitev2.pages.timesheet.PeriodType;
import tech.artcoded.websitev2.pages.timesheet.Timesheet;
import tech.artcoded.websitev2.pages.timesheet.TimesheetRepository;
import tech.artcoded.websitev2.utils.helper.DateHelper;

/**
 * The hours worked per year, month and client, without the student jobs (worked as an employee). Cached, invalidated
 * when a timesheet is saved or deleted.
 */
@Service
public class TimesheetStatsService {
    public static final String CACHE = "timesheet_stats_v3";

    private final TimesheetRepository repository;

    public TimesheetStatsService(TimesheetRepository repository) {
        this.repository = repository;
    }

    /** cached per year: the year in progress changes on the 1st of january */
    @Cacheable(cacheNames = CACHE, key = "'all-' + T(java.time.Year).now().getValue()")
    public TimesheetStats stats() {
        return compute(repository.findAll(), Year.now().getValue());
    }

    /**
     * @param currentYear
     *            the year in progress: its days worked are given even without a timesheet every month
     */
    static TimesheetStats compute(Collection<Timesheet> timesheets, int currentYear) {
        record Key(int year, int month, String client) {
        }
        var totals = new LinkedHashMap<Key, long[]>();
        var datesPerYear = new TreeMap<Integer, Set<LocalDate>>();
        var monthsPerYear = new TreeMap<Integer, Set<Integer>>();
        for (var ts : timesheets) {
            // a student job is a job as an employee, not the activity of the company
            if (ts.getYearMonth() == null || ts.isStudent()) {
                continue;
            }
            var key = new Key(ts.getYear(), ts.getMonth(), ts.getClientNameOrNA());
            monthsPerYear.computeIfAbsent(ts.getYear(), y -> new HashSet<>()).add(ts.getMonth());
            var total = totals.computeIfAbsent(key, k -> new long[2]);
            for (var period : ts.getPeriods() == null
                    ? List.<tech.artcoded.websitev2.pages.timesheet.TimesheetPeriod> of()
                    : ts.getPeriods()) {
                if (!PeriodType.WORKING_DAY.equals(period.getPeriodType())) {
                    continue;
                }
                var minutes = period.getDuration();
                total[0] += minutes;
                if (minutes > 0) {
                    total[1]++;
                    if (period.getDate() != null) {
                        datesPerYear.computeIfAbsent(ts.getYear(), y -> new HashSet<>())
                                .add(DateHelper.toLocalDate(period.getDate()));
                    }
                }
            }
        }
        var rows = new ArrayList<TimesheetStats.Row>();
        totals.forEach((k, v) -> rows.add(new TimesheetStats.Row(k.year(), k.month(), k.client(), v[0], v[1])));
        rows.sort(Comparator.comparingInt(TimesheetStats.Row::year).thenComparingInt(TimesheetStats.Row::month)
                .thenComparing(TimesheetStats.Row::client));
        var years = new ArrayList<>(new TreeSet<>(rows.stream().map(TimesheetStats.Row::year).toList()));
        // clients by hours, most worked first
        var minutesPerClient = new LinkedHashMap<String, Long>();
        rows.forEach(r -> minutesPerClient.merge(r.client(), r.minutes(), Long::sum));
        var clients = new ArrayList<>(minutesPerClient.entrySet().stream()
                .sorted(Map.Entry.<String, Long> comparingByValue().reversed()).map(Map.Entry::getKey).toList());
        var workedDays = new TreeMap<Integer, Long>();
        // only the years with a timesheet every month (otherwise the days worked are not all known), and the current
        // one
        datesPerYear.forEach((year, dates) -> {
            if (year == currentYear || monthsPerYear.getOrDefault(year, Set.of()).size() == 12) {
                workedDays.put(year, (long) dates.size());
            }
        });
        return new TimesheetStats(years, clients, rows, workedDays);
    }
}
