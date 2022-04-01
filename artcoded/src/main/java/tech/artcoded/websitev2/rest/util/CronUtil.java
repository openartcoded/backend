package tech.artcoded.websitev2.rest.util;

import org.springframework.scheduling.support.CronExpression;
import tech.artcoded.websitev2.api.helper.DateHelper;

import java.util.Date;

import static java.util.Optional.ofNullable;

public interface CronUtil {
  static Date getNextDateFromCronExpression(String cronExpression, Date lastDate) {
    var next = CronExpression.parse(cronExpression).next(DateHelper.toLocalDateTime(lastDate));
    return ofNullable(next).map(DateHelper::toDate).orElse(null);
  }

  static boolean isValidCronExpression(String cronExpression) {
    return CronExpression.isValidExpression(cronExpression);
  }
}
