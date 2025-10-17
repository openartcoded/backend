package tech.artcoded.websitev2.mongodb.converters;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import tech.artcoded.websitev2.utils.helper.DateHelper;

import java.time.YearMonth;
import java.util.Date;

@ReadingConverter
public class DateToYearMonthConverter implements Converter<Date, YearMonth> {

    @Override
    public YearMonth convert(Date date) {
        return YearMonth.from(DateHelper.toLocalDate(date));
    }
}
