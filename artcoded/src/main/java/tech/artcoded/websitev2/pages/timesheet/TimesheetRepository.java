package tech.artcoded.websitev2.pages.timesheet;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

public interface TimesheetRepository extends MongoRepository<Timesheet, String> {
  Optional<Timesheet> findByName(String name);

  Optional<Timesheet> findByNameAndClientId(String name, String clientId);

  Page<Timesheet> findByOrderByYearMonthDesc(Pageable pageable);

  Page<Timesheet> findByOrderByYearMonthAsc(Pageable pageable);

  List<Timesheet> findByYearMonth(YearMonth yearMonth);
}
