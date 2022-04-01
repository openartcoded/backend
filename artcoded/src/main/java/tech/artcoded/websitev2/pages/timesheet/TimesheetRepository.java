package tech.artcoded.websitev2.pages.timesheet;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface TimesheetRepository extends MongoRepository<Timesheet, String> {
  Optional<Timesheet> findByName(String name);

  Page<Timesheet> findByOrderByYearMonthDesc(Pageable pageable);

  Page<Timesheet> findByOrderByYearMonthAsc(Pageable pageable);
}
